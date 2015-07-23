package io.github.kardeiz.disco
package app

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServlet

import javax.servlet.{ServletConfig, ServletContext}

import javax.ws.rs.{DefaultValue, GET, Path, PathParam, Produces, QueryParam, NotFoundException}
import javax.ws.rs.core.{Context, Response, UriInfo, UriBuilder}
import javax.ws.rs.ext.{Provider, ExceptionMapper}
import javax.annotation._

import org.dspace.core.{Context => DSpaceContext}
import org.dspace.eperson.EPerson
import org.dspace.content._
import org.dspace.handle.HandleManager

trait Base extends richness.appImplicits {
  
  def request: HttpServletRequest
  def response: HttpServletResponse
  def servletContext: ServletContext

  private def url(
    servletPath: String,
    path: String,
    params: Iterable[(String, String)]): String = {
    val pairs = params.map { case (k, v) => utils.encodeURL(k) + "=" + utils.encodeURL(v) }
    val query = if (pairs.isEmpty) "" else pairs.mkString("?", "&", "")
    servletContext.getContextPath + servletPath + path + query
  }

  def url(path: String, params: Iterable[(String, String)]): String =
    url(request.getServletPath, path, params)
  def url(path: String): String = url(path, Iterable.empty)

  def staticUrl(path: String, params: Iterable[(String, String)] = Iterable.empty) = 
    url("/static", path, params)

  def url(dso: DSpaceObject, params: Iterable[(String, String)]): String = dso match {
    case x: Community => url(s"/communities/${x.getID}", params)
    case x: Collection => url(s"/collections/${x.getID}", params)
    case x: Item => url(s"/items/${x.getID}", params)
  }

  def url(dso: DSpaceObject): String = url(dso, Iterable.empty)

  def fullUrl(path: String, params: Iterable[(String, String)] = Iterable.empty) = {
    val pref = s"${request.getScheme}://${request.getServerName}:${request.getServerPort}"
    pref + url(path, params)
  }


  def retrieveUrl(bs: Bitstream, params: Iterable[(String, String)] = Iterable.empty) = 
    url(s"/bitstreams/${bs.getID}/retrieve", params)


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

    def get: DSpaceContext = 
      request.optAttribute[DSpaceContext](ContextKey).getOrElse(build)

    def complete {
      request.optAttribute[DSpaceContext](ContextKey).foreach(_.complete)
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

abstract class BaseController extends Base {
  @Context var request: HttpServletRequest = _
  @Context var response: HttpServletResponse = _
  @Context var servletContext: ServletContext = _

  @PreDestroy def cleanUp { 
    System.out.println("clean-up")
    dspaceContext.complete
  }
}


@Path("")
class HomeController extends BaseController with Pages { 

  @GET @Produces(Array("text/html"))
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
    Seq( TrailItem("Home", Some(url("/"))) ) ++ action.map(_ => TrailItem(title, None))

  lazy val topCommunities =
    Community.findAllTop(dspaceContext.get).to[Seq]

  lazy val action = request.optParameter("action")

  lazy val recentItems = wrappers.RecentItems(dspaceContext.get, null)

  lazy val search = wrappers.Search(dspaceContext.get, null, request)

  lazy val browse = wrappers.Browse(dspaceContext.get, null, request)

  // @GET @Path("/communities/{id}") 
  // def getCommunity(@PathParam("id") id: String): Response = { 
  //   var output = "Jersey says:\r\n"
  //   output += id + "\r\n"
  //   output += url("/what-ah")
  //   Response.status(200).`type`("text/plain").entity(output).build 
  // }

}

@Path("/communities")
class CommunitiesController extends BaseController with ContainerPages {
  
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
class CollectionsController extends BaseController with ContainerPages {
  
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
class ItemsController extends BaseController with DsoPages {
  
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
class BitstreamsController extends BaseController with Base {

  @GET @Path("{id}/retrieve")
  def retrieve(@PathParam("id") id: String): Response = { 
    val bs = Bitstream.find(dspaceContext.get, id.toInt)
    val re = bs.retrieve
    val ct = bs.getFormat.getMIMEType
    Response.status(200).`type`(ct).entity(re).build 
  }

}

@Path("/handle")
class HandleController extends BaseController with Base {

  @Context var uriInfo: UriInfo = _

  @GET @Path("{pref}/{id}")
  def show(@PathParam("pref") pref: String, @PathParam("id") id: String): Response = {
    val hdl = Seq(pref, id).mkString("/")
    val dso = HandleManager.resolveToObject(dspaceContext.get, hdl)
    val uri = UriBuilder.fromUri(uriInfo.getRequestUri).replacePath(url(dso)).build()
    Response.seeOther(uri).build
  }

}




// @Provider
// class NotFound extends ExceptionMapper[NotFoundException] with Base {
//   @Override def toResponse(ex: NotFoundException): Response = {
//     servletContext.getNamedDispatcher("default").forward(request, response)
//     null
//   }
// }

// class HelloWorld {

//   var servletConfig: Option[ServletConfig] = None

//   def servletContext = servletConfig.map(_.getServletContext).get

//   def init(conf: ServletConfig) { servletConfig = Option(conf) }

//   def doGet(request: HttpServletRequest, response: HttpServletResponse) {
//     val out = response.getWriter
//     response.setContentType("text/html") 
//     out.println("<H1>Hello from a Servlet</H1>")
//     out.println(request.getAttribute("id"))
//     out.println(request.getAttribute("action"))
//     val path = url("/foos/42", Map("help" -> "yo"))(request)
//     out.println(s"""<a href="$path">here</a>""")
//     out.println(servletContext.getContextPath)
//   }

//   def url(
//     path: String,
//     params: Iterable[(String, String)] = Iterable.empty)(request: HttpServletRequest): String = {
//     val pairs = params.map { case (k, v) => Utils.encodeURL(k) + "=" + Utils.encodeURL(v) }
//     val query = if (pairs.isEmpty) "" else pairs.mkString("?", "&", "")
//     servletContext.getContextPath + path + query
//   }

// }
