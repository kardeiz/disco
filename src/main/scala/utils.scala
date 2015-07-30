package io.github.kardeiz.disco

import java.net.URLEncoder

import java.io.File

import org.dspace.core.ConfigurationManager
import org.dspace.servicemanager.DSpaceKernelInit

import org.dspace.core.Context
import org.dspace.authenticate.AuthenticationManager
import org.dspace.authenticate.AuthenticationMethod

import scala.collection.JavaConverters._

case class TrailItem(title: String, url: Option[String] = None)

object utils {

  def authenticate(context: Context, username: String, password: String): Either[String, Int] = {
    val res = AuthenticationManager.authenticate(context, username, password, null, null)
    if (res == AuthenticationMethod.SUCCESS) Right(context.getCurrentUser.getID) else Left("Failed")
  }

  object aliases {
    type FacetDetails = (Boolean, String, Map[String, String], Long)
    type FacetsWrapper = Option[Seq[(String, Seq[FacetDetails])]]
  }

  val encodeURL = URLEncoder.encode(_: String, "UTF-8")
  
  def toInt(obj: Option[String]) = try { 
    obj.map(_.toInt) 
  } catch {
    case e: java.lang.NumberFormatException => None
  }

  def nonEmpty(obj: Option[String]) = obj.collect { case s if s.nonEmpty => s }

  def humanReadableByteCount(bytes: Long, si: Boolean = true): String = {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return s"$bytes B"
    val exp = ( Math.log(bytes) / Math.log(unit) ).toInt
    val pre = 
      (if (si) "kMGTPE" else "KMGTPE").charAt(exp - 1) + (if (si) "" else "i")
    "%.1f %sB".format( (bytes / Math.pow(unit, exp)), pre )
  }

  private def propLoader(path: String) = {
    val props = new java.util.Properties
    val file = Option( utils.getClass.getResourceAsStream(path) )
    file.foreach( props.load )
    props.asScala
  }

  object config {
    val dspaceDir = sys.env("DSPACE_DIR")
    val dspaceCfg = new File(dspaceDir, "config/dspace.cfg").getPath
    lazy val props = propLoader("/config.properties")
  }

  lazy val messages = propLoader("/messages.properties")

  object DSpace {
    def kernelImpl = DSpaceKernelInit.getKernel(null)
    def start {
      ConfigurationManager.loadConfig(config.dspaceCfg)
      if ( !kernelImpl.isRunning ) kernelImpl.start(config.dspaceDir)
    }
  }

}