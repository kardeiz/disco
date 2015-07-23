package io.github.kardeiz.disco

package richness

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

import org.dspace.content.Collection
import org.dspace.content.Community
import org.dspace.content.Item
import org.dspace.content.Bitstream
import org.dspace.content.DSpaceObject
import org.dspace.core.Context
import org.dspace.core.Constants
import org.dspace.content.service.ItemService
import org.dspace.discovery._
import org.dspace.discovery.DiscoverQuery.SORT_ORDER
import org.dspace.discovery.configuration._

import org.dspace.authorize.AuthorizeManager
import org.dspace.app.util.MetadataExposure

import scala.collection.JavaConverters._

case class RichRequest(obj: HttpServletRequest) {
  def optParameter(str: String) = Option(obj.getParameter(str))

  def optParametersMapFor(strs: Seq[String]) = strs.map { str =>
    Option(obj.getParameter(str)).map( str -> _)
  }.flatten.toMap


  def optAttribute[T : Manifest](str: String): Option[T] = 
    Option(obj.getAttribute(str)) match {
      case Some(x: T) => Option(x)
      case _ => None
    }

  def optSession = Option(obj.getSession(false))
}

case class RichSession(obj: HttpSession) {
  def optAttribute[T : Manifest](str: String): Option[T] = 
    Option(obj.getAttribute(str)) match {
      case Some(x: T) => Option(x)
      case _ => None
    }
}

case class RichDSpaceObject(obj: DSpaceObject) {

  def isHidden(context: Context) = 
    !AuthorizeManager.authorizeActionBoolean(context, obj, Constants.READ)

  def optMetadataValues(field: String, context: Context) =
    obj.getMetadataByMetadataString(field).to[Seq].filterNot { v =>
      MetadataExposure.isHidden(context, v.schema, v.element, v.qualifier)
    }.map(_.value)

}

case class RichCommunity(obj: Community) {
  lazy val ancestors = obj.getAllParents.to[Seq]
}

case class RichCollection(obj: Collection) {
  private implicit def richCommunity(obj: Community) = RichCommunity(obj)
  lazy val ancestors = Option(obj.getParentObject) match {
    case Some(parent: Community) => parent.ancestors :+ parent
    case _ => Seq.empty
  }
}

case class RichItem(obj: Item) {

  private implicit def richDSpaceObject(obj: DSpaceObject) = 
    RichDSpaceObject(obj)

  private implicit def richCollection(obj: Collection) = RichCollection(obj)

  lazy val ancestors = Option(obj.getParentObject) match {
    case Some(parent: Collection) => parent.ancestors :+ parent
    case _ => Seq.empty
  }

  def creators(context: Context): Seq[String] = {
    val fields = Seq("dc.contributor.author", "dc.creator", "dc.contributor.*")
    fields.map( x => obj.optMetadataValues(x, context) ).flatten.distinct
  }

  def optCreator(context: Context): Option[String] = creators(context) match {
    case Nil => None
    case x => Some(x.mkString("; "))
  }

  def optDate(context: Context): Option[String] =
    obj.optMetadataValues("dc.date.issued", context).headOption.orElse {
      obj.optMetadataValues("dc.date.*", context).headOption
    }


  def allMetadata(context: Context) =
    obj.getMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY).filterNot { dcv =>
      MetadataExposure.isHidden(context, dcv.schema, dcv.element, dcv.qualifier)
    }

  def optDescription(implicit context: Context) = 
    obj.optMetadataValues("dc.description.*", context).headOption

  lazy val optOriginalBundle = obj.getBundles("ORIGINAL").headOption

  lazy val originalBitstreams = 
    optOriginalBundle.map(_.getBitstreams.to[Seq]).getOrElse(Seq.empty)

  lazy val optPrimaryBitstream = optOriginalBundle.flatMap { oBundle =>
    oBundle.getBitstreams.filter(
      _.getID == oBundle.getPrimaryBitstreamID
    ).headOption.orElse(oBundle.getBitstreams.headOption)
  }

  def optPreview(context: Context) = 
    obj.getBundles("BRANDED_PREVIEW").headOption.flatMap { tBundle =>
      val visible = tBundle.getBitstreams.filterNot(x => x.isHidden(context))
      optPrimaryBitstream.flatMap { bs =>
        visible.filter(_.getName == s"${bs.getName}.preview.jpg").headOption
      }.orElse { visible.headOption }
    }

  def optThumbnail(context: Context) = 
    obj.getBundles("THUMBNAIL").headOption.flatMap { tBundle =>
      val visible = tBundle.getBitstreams.filterNot(x => x.isHidden(context))
      optPrimaryBitstream.flatMap { bs =>
        visible.filter(_.getName == s"${bs.getName}.jpg").headOption
      }.orElse { visible.headOption }
    }

  def optThumbnailForBitstream(context: Context, bitstream: Bitstream) =
    obj.getBundles("THUMBNAIL").headOption.flatMap { tBundle =>
      val visible = tBundle.getBitstreams.filterNot(x => x.isHidden(context))
      visible.filter {
        _.getName == s"${bitstream.getName}.jpg"
      }.headOption
    }

}

trait appImplicits {
  implicit def richRequest(obj: HttpServletRequest) = RichRequest(obj)
  implicit def richSession(obj: HttpSession) = RichSession(obj)
  implicit def richItem(obj: Item) = RichItem(obj)
  implicit def richCollection(obj: Collection) = RichCollection(obj)
  implicit def richCommunity(obj: Community) = RichCommunity(obj)
}

object appImplicits extends appImplicits
