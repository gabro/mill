package mill
package scalajslib
package bridge

import java.io.File

import org.scalajs.core.tools.io._
import org.scalajs.core.tools.linker.{ModuleInitializer, StandardLinker, Semantics, ModuleKind => ScalaJSModuleKind}
import org.scalajs.core.tools.logging.ScalaConsoleLogger
import org.scalajs.jsenv.ConsoleJSConsole
import org.scalajs.jsenv.nodejs._
import org.scalajs.testadapter.TestAdapter

class ScalaJSBridge extends mill.scalajslib.ScalaJSBridge {
  def link(sources: Array[File], libraries: Array[File], dest: File, main: String, fullOpt: Boolean, moduleKind: ModuleKind): Unit = {
    val semantics = fullOpt match {
        case true => Semantics.Defaults.optimized
        case false => Semantics.Defaults
    }
    val scalaJSModuleKind = moduleKind match {
      case ModuleKind.NoModule => ScalaJSModuleKind.NoModule
      case ModuleKind.CommonJSModule => ScalaJSModuleKind.CommonJSModule
    }
    val config = StandardLinker.Config()
      .withOptimizer(fullOpt)
      .withClosureCompilerIfAvailable(fullOpt)
      .withSemantics(semantics)
      .withModuleKind(scalaJSModuleKind)
    val linker = StandardLinker(config)
    val cache = new IRFileCache().newCache
    val sourceIRs = sources.map(FileVirtualScalaJSIRFile)
    val irContainers = FileScalaJSIRContainer.fromClasspath(libraries)
    val libraryIRs = cache.cached(irContainers)
    val destFile = AtomicWritableFileVirtualJSFile(dest)
    val logger = new ScalaConsoleLogger
    val initializer = Option(main).map { cls => ModuleInitializer.mainMethodWithArgs(cls, "main") }
    linker.link(sourceIRs ++ libraryIRs, initializer.toSeq, destFile, logger)
  }

  def run(config: NodeJSConfig, linkedFile: File): Unit = {
    nodeJSEnv(config)
      .jsRunner(Seq(FileVirtualJSFile(linkedFile)))
      .run(new ScalaConsoleLogger, ConsoleJSConsole)
  }

  def getFramework(config: NodeJSConfig,
                   frameworkName: String,
                   linkedFile: File): sbt.testing.Framework = {
    val env = nodeJSEnv(config)
    val tconfig = TestAdapter.Config().withLogger(new ScalaConsoleLogger)

    val adapter =
      new TestAdapter(env, Seq(FileVirtualJSFile(linkedFile)), tconfig)

    adapter
      .loadFrameworks(List(List(frameworkName)))
      .flatten
      .headOption
      .getOrElse(throw new RuntimeException("Failed to get framework"))
  }

  def nodeJSEnv(config: NodeJSConfig): NodeJSEnv = {
    new NodeJSEnv(
      NodeJSEnv.Config()
        .withExecutable(config.executable)
        .withArgs(config.args)
        .withEnv(config.env)
        .withSourceMap(config.sourceMap))
  }
}
