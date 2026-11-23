val scala3Version = "3.3.5"

lazy val root = project
  .in(file("."))
  .settings(
    name := "bank-account-system",
    version := "1.0.0",
    scalaVersion := scala3Version,
    Compile / run / mainClass := Some("bank.run"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.4" % Test
    )
  )
