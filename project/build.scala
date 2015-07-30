import sbt._
import Keys._

import play.twirl.sbt.SbtTwirl
import play.twirl.sbt.Import.TwirlKeys

object DiscoBuild extends Build {
  val Organization = "io.github.kardeiz"
  val Name = "disco"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.6"
  val DSpaceVersion = "5.2"

  lazy val localDependencies = Seq(
    "org.glassfish.jersey.containers" % "jersey-container-servlet" % "2.19",
    "javax.servlet" % "javax.servlet-api" % "3.1.0",
    "org.dspace" % "dspace-api" % DSpaceVersion,
    "org.springframework" % "spring-mock" % "2.0.8"
  ) ++ oracleDependency

  lazy val oracleDependency = if ( sys.env.get("DB_NAME") == Some("ORACLE") ) {
    Seq("com.oracle" % "ojdbc6" % "11.2.0.3.0")
  } else Seq.empty

  lazy val localSettings = Seq(
    organization := Organization,
    name := Name,
    version := Version,
    scalaVersion := ScalaVersion,
    resolvers ++= Seq(
      Classpaths.typesafeReleases,
      Resolver.mavenLocal
    ),
    TwirlKeys.templateImports ++= Seq("io.github.kardeiz.disco._", "app._"),
    initialCommands in console := """
      |import io.github.kardeiz.disco._
      |""".stripMargin,
    libraryDependencies ++= localDependencies
  ) ++ com.earldouglas.xwp.XwpPlugin.jetty(port = 3000, args = Seq("--path", "") )

  lazy val project = Project(
    "disco",
    file("."),
    settings = localSettings
  ).enablePlugins(SbtTwirl)


}