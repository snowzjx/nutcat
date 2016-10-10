//
//  Copyright 2015 Junxue Zhang
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

lazy val commonSettings = Seq (
  organization := "cn.edu.seu.cat",
  version := "1.0",
  scalaVersion := "2.11.6",
  //Akka lib for communication
  libraryDependencies ++=
    Seq("com.typesafe.akka" %% "akka-actor" % "2.3.8",
      "com.typesafe.akka" %% "akka-remote" % "2.3.8",
      "com.typesafe.akka" %% "akka-testkit" %"2.3.8" % "test"),
  //Typesafe Config Factory
  libraryDependencies += "com.typesafe" % "config" % "1.2.1",
  //Scopt lib for parsing command line arguments
  libraryDependencies += "com.github.scopt" %% "scopt" % "3.3.0",
  //Slf4j lib for logging
  libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.6.4",
  //Scala Library
  libraryDependencies += "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
  //Scalaz for functional programming
  libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.2",
  //Scala test
  libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"
)

lazy val jettySettings = Seq (
  //Jetty
  libraryDependencies ++= Seq(
    "org.eclipse.jetty" % "jetty-server" % "8.1.2.v20120308",
    "org.eclipse.jetty" % "jetty-servlet" % "8.1.2.v20120308"
  ),
  //jersey
  libraryDependencies ++= Seq(
    "com.sun.jersey" % "jersey-core" % "1.19",
    "com.sun.jersey" % "jersey-server" % "1.19",
    "com.sun.jersey" % "jersey-servlet" % "1.19",
    "com.sun.jersey" % "jersey-json" % "1.19"
  ),
  libraryDependencies ++= Seq(
    "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.5.3",
    "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-base" % "2.5.3",
    "com.fasterxml.jackson.module"  %% "jackson-module-scala" % "2.4.1"
  )
)

lazy val core = project.in(file("core"))
  .settings(commonSettings: _*)
  .dependsOn(api, metrics, ui, common)

lazy val api = project.in(file("api"))
  .settings(commonSettings: _*)
  .dependsOn(common)

lazy val metrics = project.in(file("metrics"))
  .settings(commonSettings: _*)
  .settings(
    //Sigar for getting system information
    libraryDependencies += "org.fusesource" % "sigar" % "1.6.4"
  ).dependsOn(common)

lazy val rest = project.in(file("rest"))
  .settings(commonSettings: _*)
  .settings(jettySettings: _*)
  .dependsOn(metrics, common)

lazy val ui = project.in(file("ui"))
  .settings(commonSettings: _*)
  .settings(jettySettings: _*)
  .dependsOn(rest, common)

lazy val common = project.in(file("common"))
  .settings(commonSettings: _*)

lazy val demo = project.in(file("demo"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += "org.apache.commons" % "commons-math3" % "3.0"
  )
  .dependsOn(api, common)


//Pack settings
packSettings

packMain := Map("start_cat_master" -> "cn.edu.seu.cat.deploy.master.Master",
                "start_cat_worker" -> "cn.edu.seu.cat.deploy.worker.Worker",
                "submit_cat_job" -> "cn.edu.seu.cat.deploy.client.Client")

packJvmOpts := Map("start_cat_master" -> Seq("-Xmx4096m"),
                   "start_cat_worker" -> Seq("-Xmx4096m"))