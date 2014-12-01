import AssemblyKeys._

name := "dchat"

version := "1.0"

scalaVersion := "2.11.1"

resolvers in ThisBuild ++= Seq(
  "sonatype-releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
  "Sonatype-repository" at "https://oss.sonatype.org/content/groups/public",
  "typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  "tomp2p" at "http://tomp2p.net/dev/mvn/"
)

libraryDependencies ++= Seq(
  "net.tomp2p" % "tomp2p-dht" % "5.0-Alpha26",
  "net.tomp2p" % "tomp2p-nat" % "5.0-Alpha26",
  "net.tomp2p" % "tomp2p-replication" % "5.0-Alpha26",
  "jline" % "jline" % "2.12",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "ch.qos.logback" % "logback-core" % "1.1.2"
)

assemblySettings

jarName in assembly := "dchat.jar"

mainClass in assembly := Some("dchat.Main")

mergeStrategy in assembly <<= (mergeStrategy in assembly) { old => {
  case versions if versions.endsWith("io.netty.versions.properties") => MergeStrategy.first
  case x => old(x)
}}
