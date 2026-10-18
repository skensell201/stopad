package com.skensel.stopad;

import java.io.*;
import java.util.*;
import java.util.function.*;

/** Packet fixtures use an independent TCP/IP checksum and literal wire layout. */
public final class TcpCheck {
    static final int SYN=2, ACK=16, FIN=1, RST=4;
    static int u16(byte[] b,int o) { return (b[o]&255)*256+(b[o+1]&255); }
    static long u32(byte[] b,int o) { return ((long)u16(b,o)<<16)|u16(b,o+2); }
    static void w16(byte[] b,int o,int n) { b[o]=(byte)(n>>>8);b[o+1]=(byte)n; }
    static void w32(byte[] b,int o,long n) { w16(b,o,(int)(n>>>16)); w16(b,o+2,(int)n); }
    static int sum(byte[] b) { long s=0;for(int i=0;i<b.length;i++) s+=(b[i]&255)<<(i%2==0?8:0);while(s>65535)s=(s&65535)+(s>>>16);return (int)(~s)&65535; }
    static int tcpSum(byte[] b) { byte[] pseudo=new byte[b.length-20+12];System.arraycopy(b,12,pseudo,0,8);pseudo[9]=6;w16(pseudo,10,b.length-20);System.arraycopy(b,20,pseudo,12,b.length-20);return sum(pseudo); }
    static byte[] segment(int port,long seq,long ack,int flags,int window,byte[] payload,int mss) {
        int header=mss>0?24:20; byte[] b=new byte[20+header+payload.length];
        b[0]=0x45;w16(b,2,b.length);b[8]=64;b[9]=6;
        b[12]=10;b[13]=111;b[15]=1;b[16]=10;b[17]=111;b[19]=2;
        w16(b,20,port);w16(b,22,53);w32(b,24,seq);w32(b,28,ack);b[32]=(byte)((header/4)<<4);b[33]=(byte)flags;w16(b,34,window);
        if(mss>0){b[40]=2;b[41]=4;w16(b,42,mss);}System.arraycopy(payload,0,b,20+header,payload.length);
        w16(b,10,sum(Arrays.copyOf(b,20)));w16(b,36,tcpSum(b));return b;
    }
    static byte[] frame(byte[] dns) { byte[] b=new byte[dns.length+2];w16(b,0,dns.length);System.arraycopy(dns,0,b,2,dns.length);return b; }
    static byte[] payload(byte[] b) { return Arrays.copyOfRange(b,20+((b[32]&255)>>>4)*4,b.length); }
    static void require(boolean ok,String reason) {if(!ok)throw new AssertionError(reason);}
    static final class Client {
        final List<byte[]> sent=new ArrayList<>();
        final List<byte[]> queries=new ArrayList<>();
        final List<Consumer<byte[]>> replies=new ArrayList<>();
        final TcpDnsTransport tcp;
        Client() { this(null); }
        Client(BiConsumer<byte[],Consumer<byte[]>> resolver) {
            tcp=new TcpDnsTransport(sent::add,resolver==null?(q,done)->{queries.add(q);replies.add(done);}:resolver);
        }
        long seq=1000, server, now=1; int window=8192,mss=256;
        void send(int flags,byte[] data) {byte[] b=segment(40000,seq,server,flags,window,data,flags==SYN?mss:0);tcp.accept(b,b.length,now);}
        byte[] last(){require(!sent.isEmpty(),"TCP must emit a packet");return sent.get(sent.size()-1);}
        void open() {
            send(SYN,new byte[0]);byte[] syn=last();
            require((syn[33]&255)==18 && u32(syn,28)==((seq+1)&0xffffffffL),"SYN must receive SYN-ACK acknowledging client sequence");
            require(u16(syn,20)==53 && u16(syn,22)==40000 && syn[15]==2 && syn[19]==1,"Reply endpoints reversed");
            require(sum(Arrays.copyOf(syn,20))==0 && tcpSum(syn)==0,"Valid independent IPv4/TCP checksums");
            server=(u32(syn,24)+1)&0xffffffffL;seq=(seq+1)&0xffffffffL;send(ACK,new byte[0]);sent.clear();
        }
        void data(byte[] bytes){send(ACK|8,bytes);seq=(seq+bytes.length)&0xffffffffL;}
        byte[] read(int expected) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();int index=0;
            while(bytes.size()<expected) {
                require(index<sent.size(),"Response stalled before complete DNS frame: "+bytes.size());byte[] b=sent.get(index++);
                byte[] data=payload(b);if(data.length==0)continue;
                require(data.length<=mss && data.length<=window,"Segment respects negotiated MSS and receive window");
                require(u32(b,24)==server && tcpSum(b)==0,"In-order response with correct TCP checksum");
                bytes.writeBytes(data);server=(server+data.length)&0xffffffffL;send(ACK,new byte[0]);
            }
            sent.clear();return bytes.toByteArray();
        }
    }
    static void handshake() {
        Client c=new Client();c.open();
        Client bad=new Client();byte[] b=segment(40000,1000,0,SYN,8192,new byte[0],256);b[36]^=1;bad.tcp.accept(b,b.length,1);
        require(bad.sent.isEmpty(),"Corrupt TCP checksum must be ignored");
        b=segment(40000,1000,0,SYN,8192,new byte[0],256);b[19]=3;w16(b,10,0);w16(b,10,sum(Arrays.copyOf(b,20)));w16(b,36,0);w16(b,36,tcpSum(b));bad.tcp.accept(b,b.length,1);
        require(bad.sent.isEmpty(),"Only synthetic DNS destination is handled");
    }
    static void framing() {
        Client c=new Client();c.open();byte[] q=PacketCheck.query("example.com"), wire=frame(q);
        c.data(Arrays.copyOf(wire,1));require(c.queries.isEmpty(),"Partial length prefix must wait");
        c.data(Arrays.copyOfRange(wire,1,8));require(c.queries.isEmpty(),"Partial DNS query must wait");
        c.data(Arrays.copyOfRange(wire,8,wire.length));require(c.queries.size()==1 && Arrays.equals(c.queries.get(0),q),"Split query is reconstructed once");
        long after=c.seq;c.seq-=wire.length;c.send(ACK|8,wire);c.seq=after;
        require(c.queries.size()==1,"Retransmitted query must not be resolved twice");
        byte[] answer=q.clone();answer[2]=(byte)0x81;answer[3]=(byte)0x83;c.replies.get(0).accept(answer);
        require(Arrays.equals(c.read(answer.length+2),frame(answer)),"DNS response retains its two-byte length prefix");
    }
    static void pipeline() {
        Client c=new Client();c.open();byte[] a=PacketCheck.query("doubleclick.net"),b=PacketCheck.query("example.com");
        byte[] both=new byte[a.length+b.length+4];System.arraycopy(frame(a),0,both,0,a.length+2);System.arraycopy(frame(b),0,both,a.length+2,b.length+2);c.data(both);
        require(c.queries.size()==2,"Two pipelined messages reach resolver");c.sent.clear();
        byte[] aa=a.clone();aa[2]=(byte)0x81;aa[3]=(byte)0x83;byte[] bb=b.clone();bb[2]=(byte)0x81;bb[3]=(byte)0x80;
        c.replies.get(1).accept(bb);require(c.sent.isEmpty(),"Later answer waits for earlier response to keep stream order");
        c.replies.get(0).accept(aa);ByteArrayOutputStream expected=new ByteArrayOutputStream();expected.writeBytes(frame(aa));expected.writeBytes(frame(bb));
        require(Arrays.equals(c.read(expected.size()),expected.toByteArray()),"Pipelined responses have distinct complete framing");
    }
    static void largeAndWindow() {
        Client c=new Client();c.open();c.data(frame(PacketCheck.query("example.com")));byte[] answer=new byte[6000];new Random(42).nextBytes(answer);
        c.window=0;c.send(ACK,new byte[0]);c.sent.clear();c.replies.get(0).accept(answer);require(c.sent.isEmpty(),"Zero receive window pauses response");
        c.window=128;c.send(ACK,new byte[0]);require(Arrays.equals(c.read(6002),frame(answer)),"Large response survives segmentation and window reopening");
    }
    static void lifecycle() {
        Client c=new Client();c.open();c.data(frame(PacketCheck.query("example.com")));byte[] answer=PacketCheck.query("example.com");c.replies.get(0).accept(answer);byte[] original=c.last().clone();
        c.sent.clear();c.now=1500;c.tcp.tick(c.now);require(c.sent.size()==1 && Arrays.equals(c.last(),original),"Lost response segment is retransmitted unchanged");
        c.read(answer.length+2);c.sent.clear();c.send(FIN|ACK,new byte[0]);c.seq++;
        require(c.sent.stream().anyMatch(b->(b[33]&FIN)!=0),"Client half-close drains answer then server FIN");
        Client reset=new Client();reset.open();reset.data(frame(answer));reset.send(RST|ACK,new byte[0]);reset.sent.clear();reset.replies.get(0).accept(answer);
        require(reset.sent.isEmpty(),"Late resolver callback after client RST is discarded");
        Client invalid=new Client();invalid.open();invalid.data(new byte[]{0,1,0});require((invalid.last()[33]&RST)!=0,"Invalid DNS frame closes connection");
        Client overload=new Client();for(int i=0;i<65;i++){byte[] b=segment(41000+i,1,0,SYN,8192,new byte[0],256);overload.tcp.accept(b,b.length,1);}
        require((overload.last()[33]&RST)!=0,"Connection flood is bounded");overload.tcp.tick(20000);overload.sent.clear();byte[] syn=segment(42000,1,0,SYN,8192,new byte[0],256);overload.tcp.accept(syn,syn.length,20001);
        require((overload.last()[33]&SYN)!=0,"Expired half-open connections free capacity");
        Client close=new Client();close.open();close.data(frame(answer));close.tcp.reset();close.sent.clear();close.replies.get(0).accept(answer);
        require(close.sent.isEmpty(),"Network reset discards pending replies");
    }
    static void synchronousPipeline() {
        List<Consumer<byte[]>> first=new ArrayList<>();int[] count={0};
        byte[] query=PacketCheck.query("a"),answer=query.clone();answer[2]=(byte)0x81;answer[3]=(byte)0x83;
        Client c=new Client((q,done)->{if(++count[0]<=8)first.add(done);else done.accept(answer);});c.open();
        byte[] bytes=new byte[3000*(query.length+2)];for(int i=0;i<3000;i++)System.arraycopy(frame(query),0,bytes,i*(query.length+2),query.length+2);
        for(int i=0;i<bytes.length;i+=1000)c.data(Arrays.copyOfRange(bytes,i,Math.min(i+1000,bytes.length)));
        for(Consumer<byte[]> done:first)done.accept(answer);
        byte[] expected=new byte[3000*(answer.length+2)];for(int i=0;i<3000;i++)System.arraycopy(frame(answer),0,expected,i*(answer.length+2),answer.length+2);
        require(Arrays.equals(c.read(expected.length),expected),"Buffered synchronous replies must drain without recursive stack growth");
    }
    static void idleClose() {
        Client c=new Client();c.open();c.tcp.tick(60002);require((c.last()[33]&FIN)!=0,"Idle connection sends FIN");
        c.server++;c.send(ACK,new byte[0]);c.data(frame(PacketCheck.query("example.com")));
        require(c.queries.isEmpty() && (c.last()[33]&RST)!=0,"Query after server FIN must not produce post-FIN data");
    }
    static void defensive() {
        Client ecn=new Client();byte[] syn=segment(40000,1000,0,0xc2,8192,new byte[0],256);ecn.tcp.accept(syn,syn.length,1);
        require((ecn.last()[33]&255)==18,"ECN SYN must negotiate ordinary TCP when ECN is unsupported");
        Client c=new Client();c.open();long original=c.seq;c.seq=(c.seq+0x80000000L)&0xffffffffL;
        c.send(ACK|8,new byte[]{1,2,3});c.seq=original;require(c.queries.isEmpty(),"Far-out sequence must not enter DNS stream");
        byte[] frame=frame(PacketCheck.query("example.com"));c.seq+=5;c.send(ACK|8,Arrays.copyOfRange(frame,5,frame.length));c.seq=original;
        require(c.queries.isEmpty(),"Out-of-order query suffix waits for retransmit");c.data(frame);require(c.queries.size()==1,"Retransmit after out-of-order segment recovers query");
        Client invalidAck=new Client();invalidAck.open();byte[] query=PacketCheck.query("example.com");invalidAck.data(frame(query));invalidAck.replies.get(0).accept(query);
        byte[] unacknowledged=invalidAck.last().clone();invalidAck.seq+=9000;invalidAck.server+=query.length+2;invalidAck.send(ACK,new byte[0]);invalidAck.sent.clear();invalidAck.tcp.tick(1500);
        require(invalidAck.sent.stream().anyMatch(b->Arrays.equals(b,unacknowledged)),"ACK outside receive window must not acknowledge response");
        Client wrap=new Client();wrap.seq=0xffffffffL;wrap.open();
    }
    public static void main(String[] args) {
        if(args.length>0 && args[0].equals("defensive")){defensive();return;}
        if(args.length>0 && args[0].equals("idle")){idleClose();return;}
        if(args.length>0 && args[0].equals("sync")){synchronousPipeline();return;}
        handshake();if(args.length>0 && args[0].equals("handshake"))return;
        framing();pipeline();largeAndWindow();lifecycle();defensive();idleClose();synchronousPipeline();
        System.out.println("TcpDns: handshake/checksums, split/duplicate frames, pipeline, 6KB response, MSS/window, retransmit, FIN/RST, bounds, reset, ECN/sequence validation, idle close and 3000 queued replies passed");
    }
}
