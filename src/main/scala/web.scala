package io.github.kardeiz.disco
package web

import java.net.URI

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServlet

import javax.servlet.{ServletConfig, ServletContext}

import javax.ws.rs._
import javax.ws.rs.core.{Context, Response}
import javax.annotation._

import org.dspace.core.{Context => DSpaceContext}
import org.dspace.eperson.EPerson
import org.dspace.content._
import org.dspace.handle.HandleManager
import org.dspace.authorize.AuthorizeManager

trait Base extends richness.webImplicits {
  
  def request: HttpServletRequest
  def response: HttpServletResponse
  def servletContext: ServletContext

  object RichUrl {

    def wrapQuery(queryString: String) = 
      utils.nonEmpty(Option(queryString)).map("?" + _)

    def buildQuery(params: Iterable[(String, String)]) = {
      val pairs = params.map { case (k, v) => 
        utils.encodeURL(k) + "=" + utils.encodeURL(v)
      }
      if (pairs.isEmpty) None else Option(pairs.mkString("?", "&", ""))
    }

    def apply(path: String, query: Option[String]): RichUrl = 
      apply(request.getServletPath, path, query)

    implicit def richUrlToString(richUrl: RichUrl) = richUrl.toString
    implicit def richUrlToURI(richUrl: RichUrl) = richUrl.toURI
  }

  case class RichUrl(servletPath: String, path: String, query: Option[String]) {
    override def toString = 
      servletContext.getContextPath + servletPath + path + query.getOrElse("")

    def toURI = new URI(toString)
  }

  def url(path: String, query: Option[String]): RichUrl = RichUrl(path, query)

  def url(path: String, params: Iterable[(String, String)]): RichUrl =
    url(path, RichUrl.buildQuery(params))

  def url(path: String): RichUrl = url(path, None)

  def url(dso: DSpaceObject, query: Option[String]): RichUrl = dso match {
    case x: Community => url(s"/communities/${x.getID}", query)
    case x: Collection => url(s"/collections/${x.getID}", query)
    case x: Item => url(s"/items/${x.getID}", query)
  }

  def url(dso: DSpaceObject, params: Iterable[(String, String)]): RichUrl =
    url(dso, RichUrl.buildQuery(params))

  def url(dso: DSpaceObject): RichUrl = url(dso, None)

  def staticUrl(path: String, params: Iterable[(String, String)] = Iterable.empty) = 
    RichUrl("/static", path, RichUrl.buildQuery(params))

  def fullUrl(path: String, params: Iterable[(String, String)] = Iterable.empty) = {
    val pref = s"${request.getScheme}://${request.getServerName}:${request.getServerPort}"
    pref + url(path, params)
  }

  def retrieveUrl(bs: Bitstream, params: Iterable[(String, String)] = Iterable.empty) = 
    url(s"/bitstreams/${bs.getID}/retrieve", params)

  object flash {
    val KeyNow  = "disco.flash.now"
    val KeyNext = "disco.flash.next"

    def rotateIn {
      for {
        session <- request.optSession
        obj     <- session.optAttribute[Map[String, String]](KeyNext)
      } {
        request.setAttribute(KeyNow, obj)
        session.removeAttribute(KeyNext)
      }
    }

    def rotateOut {
      for {
        obj <- request.optAttribute[Map[String, String]](KeyNext)
      } request.getSession.setAttribute(KeyNext, obj)
    }

    def now = 
      request.optAttribute[Map[String, String]](KeyNow).getOrElse(Map.empty)

    def next(key: String, value: String) = {
      val curr = request.optAttribute[Map[String, String]](KeyNext).getOrElse(Map.empty)
      request.setAttribute(KeyNext, curr + (key -> value))
    }
  }

  object dspaceContext {
    val UserIdKey  = "disco.user.id"
    val ContextKey = "disco.context"

    def build: DSpaceContext = {
      val context = new DSpaceContext
      for {
        session <- request.optSession
        id <- session.optAttribute[Int](UserIdKey)
      } context.setCurrentUser(EPerson.find(context, id))
      request.setAttribute(ContextKey, context)
      context
    }

    def userSignedIn = Option(get.getCurrentUser).nonEmpty    

    def get: DSpaceContext = 
      request.optAttribute[DSpaceContext](ContextKey).getOrElse(build)

    def complete {
      request.optAttribute[DSpaceContext](ContextKey).foreach(_.complete)
    }

    def isAdmin = AuthorizeManager.isAdmin(get)

  }

  object auth {
    def setUserId(id: Int) {
      request.getSession.setAttribute(dspaceContext.UserIdKey, id)
    }

    def unsetUserId {
      request.optSession.foreach(_.removeAttribute(dspaceContext.UserIdKey))
    }

    def adminOnly {
      if (!dspaceContext.isAdmin) {
        flash.next("danger", "Not Authorized")
        val response = Response.seeOther(url("/")).build
        throw new WebApplicationException(response)
      }
    }
  }

}

trait Pages extends Base {
  def baseUrl(params: Map[String, String]): String
}

trait DsoPages extends Pages {
  def dso: DSpaceObject  
  def ancestors: Seq[DSpaceObject]
  
  def baseUrl(params: Map[String, String] = Map.empty) = 
    url(dso, params)
}

trait ContainerPages extends DsoPages {

  lazy val action = request.optParameter("action")

  lazy val trailItems = {
    val home = TrailItem("Home", Some(url("/")))
    val curr = (ancestors :+ dso).map(x => TrailItem(x.getName, Some(url(x))))
    Seq(home) ++ curr ++ action.map(_ => TrailItem(title, None))
  }

  lazy val recentItems = wrappers.RecentItems(dspaceContext.get, dso)
  lazy val search = wrappers.Search(dspaceContext.get, dso, request)
  lazy val browse = wrappers.Browse(dspaceContext.get, dso, request)

  lazy val title = if ( action == Some("browse") ) {
    request.optParameter("type").map { tp => 
      utils.messages(s"browse.by.name.$tp").format(dso.getName)
    }.getOrElse(utils.messages(s"browse.default").format(dso.getName))
  } else if ( action == Some("search") ) {
    utils.messages("search.page").format(dso.getName)
  } else dso.getName

  lazy val subTitle = 
    if (action == Some("browse")) request.optParameter("value") else None

}

abstract class ApplicationController extends Base { 
  @Context var request: HttpServletRequest = _
  @Context var response: HttpServletResponse = _
  @Context var servletContext: ServletContext = _

  @PostConstruct def before { flash.rotateIn }

  @PreDestroy def after { 
    flash.rotateOut
    dspaceContext.complete
  }
}


@Path("")
class HomeController extends ApplicationController with Pages { 

  @GET
  def index: Response = { 
    val output = views.html.home(this).toString
    Response.status(200).entity(output).build 
  }

  lazy val title = if ( action == Some("browse") ) {
    request.optParameter("type").map { tp => 
      utils.messages(s"browse.by.name.$tp").format("repository")
    }.getOrElse(utils.messages(s"browse.default").format("repository"))
  } else if ( action == Some("search") ) {
    utils.messages("search.page").format("repository")
  } else "Home"

  lazy val subTitle = 
    if (action == Some("browse")) request.optParameter("value") else None

  def baseUrl(params: Map[String, String] = Map.empty) = url("/", params)

  lazy val trailItems = 
    Seq(TrailItem("Home", Some(url("/")))) ++ action.map(_ => TrailItem(title, None))

  lazy val topCommunities = Community.findAllTop(dspaceContext.get).to[Seq]

  lazy val action = request.optParameter("action")

  lazy val recentItems = wrappers.RecentItems(dspaceContext.get, null)

  lazy val search = wrappers.Search(dspaceContext.get, null, request)

  lazy val browse = wrappers.Browse(dspaceContext.get, null, request)


}

@Path("/communities")
class CommunitiesController extends ApplicationController with ContainerPages {
  
  var dso: Community = _

  @GET @Path("{id}")
  def show(@PathParam("id") id: String): Response = {
    dso = Community.find(dspaceContext.get, id.toInt)
    val output = views.html.community(this).toString
    Response.status(200).entity(output).build 
  }

  lazy val ancestors = dso.ancestors

  lazy val subcommunities = dso.getSubcommunities.to[Seq]

  lazy val collections = dso.getCollections.to[Seq]

}

@Path("/collections")
class CollectionsController extends ApplicationController with ContainerPages {
  
  var dso: Collection = _

  @GET @Path("{id}")
  def show(@PathParam("id") id: String): Response = {
    dso = Collection.find(dspaceContext.get, id.toInt)
    val output = views.html.collection(this).toString
    Response.status(200).entity(output).build 
  }

  lazy val ancestors = dso.ancestors
}

@Path("/items")
class ItemsController extends ApplicationController with DsoPages {
  
  var dso: Item = _

  @GET @Path("{id}")
  def show(@PathParam("id") id: String): Response = {
    dso = Item.find(dspaceContext.get, id.toInt)
    val output = views.html.item(this).toString
    Response.status(200).entity(output).build 
  }

  lazy val title = dso.getName

  lazy val trailItems = {
    val home = TrailItem("Home", Some(url("/")))
    val curr = (ancestors :+ dso).map(x => TrailItem(x.getName, Some(url(x))))
    Seq(home) ++ curr
  }

  lazy val ancestors = dso.ancestors

  lazy val optParent = 
    ancestors.lastOption.collect { case coll: Collection => coll }

}

@Path("/bitstreams")
class BitstreamsController extends ApplicationController with Base {

  @GET @Path("{id}/retrieve")
  def retrieve(@PathParam("id") id: String): Response = { 
    val bs = Bitstream.find(dspaceContext.get, id.toInt)
    val re = bs.retrieve
    val ct = bs.getFormat.getMIMEType
    Response.status(200).`type`(ct).entity(re).build 
  }

}

@Path("/handle")
class HandleController extends ApplicationController with Base {

  @GET @Path("{pref}/{id}")
  def show(@PathParam("pref") pref: String, @PathParam("id") id: String): Response = {
    val dso = HandleManager.resolveToObject(dspaceContext.get, s"${pref}/${id}")
    val uri = url(dso, RichUrl.wrapQuery(request.getQueryString))
    Response.seeOther(uri).build
  }
}

@Path("")
class SessionsController extends ApplicationController with Base {

  @GET @Path("/login")
  def `new`: Response = { 
    val output = views.html.login(this).toString
    Response.status(200).entity(output).build 
  }

  @GET @Path("/logout")
  def delete: Response = {
    auth.unsetUserId
    flash.next("success", "Logout successful")
    Response.seeOther(url("/")).build
  }

  @POST @Path("/login") @Consumes(Array("application/x-www-form-urlencoded"))
  def create(
    @FormParam("username") username: String,
    @FormParam("password") password: String): Response = {

    val loc = utils.authenticate(dspaceContext.get, username, password) match {
      case Right(id) => {
        auth.setUserId(id)
        flash.next("success", "Login successful")
        url("/")
      }
      case Left(msg) => {
        flash.next("danger", "Login failed")
        url("/login")
      }
    }
    Response.seeOther(loc).build
  }
}

@Path("/admin")
class AdminController extends ApplicationController with Base {
  
  import org.dspace.content.WorkspaceItem
  import org.dspace.content.MetadataSchema
  import org.dspace.browse.IndexBrowse
  import org.dspace.content.InstallItem

  import org.glassfish.jersey.media.multipart._

  @GET @Path("/collections/{id}/items/add")
  def addItemGet(@PathParam("id") id: String): Response = {
    auth.adminOnly
    val output = views.html.addItem(this).toString
    Response.status(200).entity(output).build 
  }

  @POST @Path("/collections/{id}/items/add") 
  @Consumes(Array("multipart/form-data", "application/x-www-form-urlencoded"))
  def addItemPost(
    @PathParam("id") id: String,
    @FormDataParam("title") title: String,
    @FormDataParam("file") file: java.io.InputStream,
    @FormDataParam("file") contentDispositionHeader: FormDataContentDisposition): Response = {
    
    auth.adminOnly

    val collection = Collection.find(dspaceContext.get, utils.toInt(id).get)
    val workspaceItem = WorkspaceItem.create(dspaceContext.get, collection, false)
    
    var item = workspaceItem.getItem

    item.addMetadata(MetadataSchema.DC_SCHEMA, "title", null, null, title)

    Option(file).foreach { _file =>
      val name = contentDispositionHeader.getFileName
      val bitstream = item.createSingleBitstream(_file)
      bitstream.setName(name)
      bitstream.update
    }

    workspaceItem.update

    (new IndexBrowse).indexItem(item)

    item = InstallItem.installItem(dspaceContext.get, workspaceItem)


    flash.next("success", "Added item")
    Response.seeOther(url(s"/admin/collections/${id}/items/add")).build
  }

}
