package com.skensel.stopad;

import java.util.*;
import java.util.function.*;
import java.security.SecureRandom;

/** Bounded IPv4 DNS/TCP endpoint. No Internet default route or generic TCP proxy. */
final class TcpDnsTransport {
    private final Consumer<byte[]> sink;
    private final BiConsumer<byte[], Consumer<byte[]>> resolver;
    private final Map<Key, Flow> flows = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private record Key(int address, int port) { }
    private record Segment(Key key, long sequence, long acknowledgment, int flags, int window, int mss, byte[] data) { }
    private static final int MAX_FLOWS=64, MAX_INPUT=65537, MAX_OUTPUT=131074;
    private long clock;
    private static final class Request { byte[] answer; }
    private static final class Chunk {
        final byte[] bytes; int offset;
        Chunk(byte[] bytes) { this.bytes=bytes; }
    }
    private static final class Flow {
        final Key key;
        final int mss;
        long receive, next, activity, sentAt, expires;
        int window, inputSize, outputSize, retries, pendingSize;
        boolean established, remoteFin, localFin, pendingFin, draining;
        byte[] input, pending;
        final ArrayDeque<Request> requests=new ArrayDeque<>();
        final ArrayDeque<Chunk> output=new ArrayDeque<>();
        Flow(Segment segment, long initial, long now) {
            key=segment.key; receive=add(segment.sequence,1); next=initial; mss=segment.mss; window=segment.window; activity=now;
        }
    }
    TcpDnsTransport(Consumer<byte[]> sink, BiConsumer<byte[], Consumer<byte[]>> resolver) { this.sink=sink; this.resolver=resolver; }
    synchronized void accept(byte[] packet, int length, long now) {
        clock=now; Segment segment=parse(packet,length); if(segment==null)return;
        Flow flow=flows.get(segment.key);
        if(flow==null) {
            if((segment.flags&4)!=0)return;
            if((segment.flags&~0xc0)!=2 || segment.data.length!=0) { reject(segment);return; }
            long active=flows.values().stream().filter(x->x.expires==0).count();
            if(active>=MAX_FLOWS) { reject(segment);return; }
            flow=new Flow(segment,Integer.toUnsignedLong(random.nextInt()),now);flows.put(segment.key,flow);
            transmit(flow,18,new byte[0]);return;
        }
        if(flow.expires!=0) { emit(flow,flow.next,16,new byte[0]);return; }
        if((segment.flags&4)!=0) {
            if(segment.sequence==flow.receive) flows.remove(flow.key); else emit(flow,flow.next,16,new byte[0]);
            return;
        }
        if((segment.flags&2)!=0) {
            if(!flow.established && add(segment.sequence,1)==flow.receive && flow.pending!=null) sink.accept(flow.pending.clone());
            else emit(flow,flow.next,16,new byte[0]);
            return;
        }
        if((segment.flags&16)==0)return;
        int delta=(int)(segment.sequence-flow.receive);
        if(delta>Math.min(8192,MAX_INPUT-flow.inputSize) || delta< -65535) { emit(flow,flow.next,16,new byte[0]);return; }
        if(after(segment.acknowledgment,flow.next)) { emit(flow,flow.next,16,new byte[0]);return; }
        if(!flow.established) {
            if(segment.acknowledgment!=flow.next || segment.sequence!=flow.receive) { reject(segment);return; }
            flow.established=true;
        }
        flow.activity=now; flow.window=segment.window;
        if(flow.pending!=null && segment.acknowledgment==flow.next) {
            flow.pending=null; flow.outputSize-=flow.pendingSize; flow.pendingSize=0;flow.retries=0;
            if(flow.pendingFin) { flow.pendingFin=false;if(flow.remoteFin) { timeWait(flow);return; } }
        }
        if(delta>0) { emit(flow,flow.next,16,new byte[0]);sendNext(flow);return; }
        int skip=Math.min(segment.data.length,-delta);
        int count=segment.data.length-skip;
        if(count>0) {
            if(flow.remoteFin || flow.localFin || count>MAX_INPUT-flow.inputSize) { abort(flow);return; }
            if(flow.input==null)flow.input=new byte[MAX_INPUT];
            System.arraycopy(segment.data,skip,flow.input,flow.inputSize,count);flow.inputSize+=count;flow.receive=add(flow.receive,count);
            emit(flow,flow.next,16,new byte[0]);drain(flow);
            if(flows.get(flow.key)!=flow)return;
        } else if(segment.data.length>0) emit(flow,flow.next,16,new byte[0]);
        if((segment.flags&1)!=0 && add(segment.sequence,segment.data.length)==flow.receive) {
            flow.receive=add(flow.receive,1); flow.remoteFin=true;emit(flow,flow.next,16,new byte[0]);
            if(flow.inputSize!=0) { abort(flow);return; }
            if(flow.localFin && flow.pending==null) { timeWait(flow);return; }
        }
        sendNext(flow);
    }
    private void drain(Flow flow) {
        if(flow.draining)return;
        flow.draining=true;
        try {
        while(flow.inputSize>=2 && flow.requests.size()<8 && flows.get(flow.key)==flow) {
            int length=DnsPacket.u16(flow.input,0);
            if(length<12) { abort(flow);return; }
            if(flow.inputSize<length+2)return;
            byte[] query=Arrays.copyOfRange(flow.input,2,length+2);
            System.arraycopy(flow.input,length+2,flow.input,0,flow.inputSize-length-2);flow.inputSize-=length+2;
            Request request=new Request();flow.requests.add(request);
            try { resolver.accept(query,answer->complete(flow,request,answer)); }
            catch(RuntimeException failure) { abort(flow);return; }
        }
        } finally { flow.draining=false; }
    }
    private synchronized void complete(Flow flow,Request request,byte[] answer) {
        if(flows.get(flow.key)!=flow || flow.expires!=0 || request.answer!=null)return;
        if(answer==null || answer.length<12 || answer.length>65535) { abort(flow);return; }
        request.answer=answer;
        while(!flow.requests.isEmpty() && flow.requests.peek().answer!=null) {
            byte[] dns=flow.requests.remove().answer;
            if(flow.outputSize+dns.length+2>MAX_OUTPUT) { abort(flow);return; }
            byte[] framed=new byte[dns.length+2];DnsPacket.put16(framed,0,dns.length);System.arraycopy(dns,0,framed,2,dns.length);
            flow.output.add(new Chunk(framed));flow.outputSize+=framed.length;
        }
        drain(flow);if(flows.get(flow.key)==flow)sendNext(flow);
    }
    private void sendNext(Flow flow) {
        if(flow.pending!=null || flow.expires!=0)return;
        if(!flow.output.isEmpty() && flow.window>0) {
            Chunk chunk=flow.output.peek();int count=Math.min(Math.min(flow.mss,flow.window),chunk.bytes.length-chunk.offset);
            byte[] bytes=Arrays.copyOfRange(chunk.bytes,chunk.offset,chunk.offset+count);chunk.offset+=count;
            if(chunk.offset==chunk.bytes.length)flow.output.remove();transmit(flow,24,bytes);
        } else if(flow.remoteFin && flow.requests.isEmpty() && flow.output.isEmpty() && !flow.localFin) {
            flow.localFin=true;transmit(flow,17,new byte[0]);
        }
    }
    private void transmit(Flow flow,int flags,byte[] data) {
        long sequence=flow.next;flow.next=add(flow.next,data.length+((flags&3)!=0?1:0));
        flow.pendingSize=data.length;flow.pendingFin=(flags&1)!=0;flow.sentAt=clock;flow.retries=0;
        flow.pending=wire(flow,sequence,flags,data);sink.accept(flow.pending.clone());
    }
    private void timeWait(Flow flow) {
        flow.expires=clock+120000;flow.input=null;flow.pending=null;flow.requests.clear();flow.output.clear();
        // Closed flows retain only sequence state. Bound tombstones independently of active sessions.
        if(flows.size()>MAX_FLOWS+256) for(Flow old:new ArrayList<>(flows.values())) {
            if(old.expires!=0 && old!=flow) { flows.remove(old.key);break; }
        }
    }
    private void abort(Flow flow) { flows.remove(flow.key);emit(flow,flow.next,20,new byte[0]); }
    private void reject(Segment segment) {
        Flow temporary=new Flow(segment,0,clock);
        temporary.receive=add(segment.sequence,segment.data.length+((segment.flags&3)!=0?1:0));
        emit(temporary,(segment.flags&16)!=0?segment.acknowledgment:0,(segment.flags&16)!=0?4:20,new byte[0]);
    }
    synchronized void tick(long now) {
        clock=now;
        for(Flow flow:new ArrayList<>(flows.values())) {
            if(flow.expires!=0) { if(now>=flow.expires)flows.remove(flow.key);continue; }
            if(!flow.established && now-flow.activity>=10000) { flows.remove(flow.key);continue; }
            if(flow.pending!=null && now-flow.sentAt>=Math.min(8000,1000L<<flow.retries)) {
                if(flow.retries>=5) { abort(flow);continue; }
                flow.retries++;flow.sentAt=now;sink.accept(flow.pending.clone());
            }
            if(flow.established && now-flow.activity>=60000) {
                if(flow.pending!=null || !flow.requests.isEmpty()) { abort(flow);continue; }
                if(!flow.localFin) { flow.localFin=true;flow.activity=now;transmit(flow,17,new byte[0]); }
                else abort(flow);
            }
        }
    }
    synchronized void reset() { flows.clear(); }
    private static boolean after(long a,long b) { return (int)(a-b)>0; }
    private void emit(Flow flow,long sequence,int flags,byte[] data) { sink.accept(wire(flow,sequence,flags,data)); }
    private byte[] wire(Flow flow,long sequence,int flags,byte[] data) {
        int h=(flags&2)!=0?24:20;byte[] packet=new byte[20+h+data.length];
        packet[0]=0x45;DnsPacket.put16(packet,2,packet.length);packet[8]=64;packet[9]=6;
        packet[12]=10;packet[13]=111;packet[15]=2;put32(packet,16,Integer.toUnsignedLong(flow.key.address));
        DnsPacket.put16(packet,20,53);DnsPacket.put16(packet,22,flow.key.port);put32(packet,24,sequence);put32(packet,28,flow.receive);
        packet[32]=(byte)((h/4)<<4);packet[33]=(byte)flags;DnsPacket.put16(packet,34,Math.min(8192,MAX_INPUT-flow.inputSize));
        if(h==24){packet[40]=2;packet[41]=4;DnsPacket.put16(packet,42,1460);}
        System.arraycopy(data,0,packet,20+h,data.length);DnsPacket.put16(packet,10,DnsPacket.checksum(packet,0,20));DnsPacket.put16(packet,36,tcpChecksum(packet,20,packet.length-20));return packet;
    }
    private static Segment parse(byte[] packet,int length) {
        if(length<40 || length>packet.length || (packet[0]&0xf0)!=0x40)return null;
        int h=(packet[0]&15)*4;
        if(h<20 || h+20>length || DnsPacket.u16(packet,2)!=length || packet[9]!=6 || (DnsPacket.u16(packet,6)&0x3fff)!=0
                || DnsPacket.checksum(packet,0,h)!=0 || packet[16]!=10 || packet[17]!=111 || packet[18]!=0 || packet[19]!=2
                || DnsPacket.u16(packet,h+2)!=53 || tcpChecksum(packet,h,length-h)!=0)return null;
        int th=(packet[h+12]&0xf0)>>>2;if(th<20 || h+th>length)return null;
        int mss=536;
        for(int i=h+20;i<h+th;) {
            int kind=packet[i]&255;if(kind==0)break;if(kind==1){i++;continue;}
            if(i+1>=h+th)return null;int n=packet[i+1]&255;if(n<2 || i+n>h+th)return null;
            if(kind==2 && n==4)mss=Math.max(1,Math.min(1460,DnsPacket.u16(packet,i+2)));i+=n;
        }
        return new Segment(new Key((int)get32(packet,12),DnsPacket.u16(packet,h)),get32(packet,h+4),get32(packet,h+8),packet[h+13]&255,DnsPacket.u16(packet,h+14),mss,Arrays.copyOfRange(packet,h+th,length));
    }
    private static int tcpChecksum(byte[] packet,int h,int length) {
        byte[] pseudo=new byte[12+length];System.arraycopy(packet,12,pseudo,0,8);pseudo[9]=6;DnsPacket.put16(pseudo,10,length);System.arraycopy(packet,h,pseudo,12,length);return DnsPacket.checksum(pseudo,0,pseudo.length);
    }
    private static long get32(byte[] packet,int i){return ((long)DnsPacket.u16(packet,i)<<16)|DnsPacket.u16(packet,i+2);}
    private static void put32(byte[] packet,int i,long n){DnsPacket.put16(packet,i,(int)(n>>>16));DnsPacket.put16(packet,i+2,(int)n);}
    private static long add(long n,int amount){return (n+amount)&0xffffffffL;}
}
