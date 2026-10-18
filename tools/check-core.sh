#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
check_dir=$(mktemp -d)
trap 'rm -rf "$check_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d "$check_dir" app/src/main/java/com/skensel/stopad/DomainRules.java app/src/main/java/com/skensel/stopad/DnsPacket.java app/src/main/java/com/skensel/stopad/DomainPolicy.java app/src/main/java/com/skensel/stopad/DnsUpstream.java tools/DomainRulesCheck.java tools/PacketCheck.java tools/PolicyCheck.java tools/UpstreamCheck.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$check_dir" DomainRulesCheck
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$check_dir" com.skensel.stopad.PacketCheck
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$check_dir" com.skensel.stopad.PolicyCheck
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$check_dir" com.skensel.stopad.UpstreamCheck
