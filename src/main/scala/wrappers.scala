package io.github.kardeiz.disco
package wrappers

import org.dspace.browse._
import org.dspace.browse.BrowseEngine
import org.dspace.browse.BrowseException
import org.dspace.browse.BrowseIndex
import org.dspace.browse.BrowseInfo
import org.dspace.browse.BrowserScope
import org.dspace.content.DSpaceObject
import org.dspace.content.Item
import org.dspace.content.Community
import org.dspace.content.Collection
import org.dspace.content.service.ItemService
import org.dspace.content.Thumbnail
import org.dspace.content.Bitstream
import org.dspace.core.ConfigurationManager
import org.dspace.core.Constants
import org.dspace.core.Context
import org.dspace.discovery._
import org.dspace.discovery.DiscoverQuery.SORT_ORDER
import org.dspace.discovery.configuration._
import org.dspace.eperson.EPerson
import org.dspace.servicemanager.DSpaceKernelInit
import org.dspace.sort.SortOption
import org.dspace.sort.SortException

import org.dspace.authorize.AuthorizeManager

import scala.collection.JavaConverters._

import scala.collection.mutable

import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest

import richness.appImplicits._

object RecentItems {
  def apply(context: Context, dso: DSpaceObject, offset: Int = 0) = {
    val discoveryConfig = SearchUtils.getDiscoveryConfiguration(dso)
    val optConfig = Option(discoveryConfig.getRecentSubmissionConfiguration)
    optConfig.map { config =>
      val query = new DiscoverQuery
      query.addFilterQueries(
        discoveryConfig.getDefaultFilterQueries.asScala : _*)
      query.setDSpaceObjectFilter(Constants.ITEM)
      query.setMaxResults(config.getMax)
      query.setStart(offset)
      Option(SearchUtils.getSearchService.toSortFieldIndex(
        config.getMetadataSortField, config.getType
      )).foreach( query.setSortField(_, DiscoverQuery.SORT_ORDER.desc))
      (SearchUtils.getSearchService
        .search(context, dso, query)
        .getDspaceObjects
        .asScala
        .collect { case item: Item => item }
        .to[Seq])
    }.getOrElse(Seq.empty)
  }
}

object Browse {
  val orderOpts = Seq(("ASC", "ascending"), ("DESC", "descending"))
  val rppOpts   = (20 to 100 by 20).toSeq.map( x => (x, x))
  val types     = Seq("dateissued", "author", "title", "subject")
  val fields    = Seq("type", "order", "value", "starts_with", "offset", "rpp")
  case class StringResult(value: String, auth: String, count: String)
}

case class Browse(context: Context, dso: DSpaceObject, request: HttpServletRequest) {

  object params {
    lazy val map = request.optParametersMapFor(Browse.fields)
    val `type`     = request.optParameter("type")
    val order      = request.optParameter("order")
    val value      = request.optParameter("value")
    val startsWith = request.optParameter("starts_with")
    val offset     = request.optParameter("offset")
    val rpp        = request.optParameter("rpp")
  }

  def toMap = Map("action" -> "browse") ++ params.map

  val browseIndex = params.`type`.map(BrowseIndex.getBrowseIndex).getOrElse {
    BrowseIndex.getBrowseIndex(SortOption.getDefaultSortOption)
  }

  val `type` = params.`type`.getOrElse("dateissued")
  val order  = params.order.getOrElse(browseIndex.getDefaultOrder)    
  
  val offset = utils.toInt(params.offset).getOrElse(0)
  val rpp    = utils.toInt(params.rpp).getOrElse(20)

  val startsWith = utils.nonEmpty(params.startsWith)
  val value      = utils.nonEmpty(params.value)

  val level = if (value.isEmpty) 0 else 1

  lazy val sortBy = 
    Option(browseIndex.getSortOption).map(_.getNumber).getOrElse(0)

  lazy val browserScope = {
    val browserScope = new BrowserScope(context)
    browserScope.setBrowseIndex(browseIndex)
    browserScope.setOrder(order)
    browserScope.setOffset(offset)
    browserScope.setResultsPerPage(rpp)
    browserScope.setBrowseLevel(level)
    value.foreach( browserScope.setFilterValue )
    startsWith.foreach( browserScope.setStartsWith )
   
    dso match {
      case o: Community =>  browserScope.setBrowseContainer(o)
      case o: Collection => browserScope.setBrowseContainer(o)
      case _ =>
    }
    browserScope.setSortBy(sortBy)
    browserScope
  }  

  lazy val browseInfo = (new BrowseEngine(context)).browse(browserScope)

  lazy val browseResults = {
    val isItemIndex   = browseInfo.getBrowseIndex.isItemIndex
    val isSecondLevel = browseInfo.isSecondLevel
    if (isItemIndex || isSecondLevel) {
      browseInfo.getItemResults(context).to[Seq]
    } else {
      browseInfo.getStringResults.to[Seq].map { row =>
        val Seq(value, auth, count) = row.to[Seq]
        Browse.StringResult(value, auth, count)
      }
    }
  }

}

object Search {
  val filterRegex = "filter_type.*".r
  val orderOpts = Seq(("ASC", "ascending"), ("DESC", "descending"))
  val rppOpts   = (10 +: (20 to 100 by 20).toSeq).map( x => (x, x))
  val sortByOpts = Seq(
    ("score", "Score"),
    ("dc.date.issued_dt", "Date"),
    ("dc.title_sort", "Title")
  )
  val fields = Seq("query", "order", "sort_by", "page", "rpp")

  case class FilterEntry(field: String, `type`: String, value: String)

}

case class Search(context: Context, dso: DSpaceObject, request: HttpServletRequest) {

  object params {
    lazy val map = request.optParametersMapFor(Search.fields)

    val filters = request.getParameterNames.asScala.filter {
      Search.filterRegex.pattern.matcher(_).matches
    }.map(_.takeRight(1)).to[Seq].distinct.map { i =>
      ( 
        request.optParameter(s"filter_field_$i"),
        request.optParameter(s"filter_type_$i"),        
        request.optParameter(s"filter_value_$i") 
      )
    }.collect { 
      case ( Some(x), Some(y), Some(z) ) => Search.FilterEntry(x, y, z)
    }
    val query   = request.optParameter("query")
    val sortBy  = request.optParameter("sort_by")
    val order   = request.optParameter("order")
    val page    = request.optParameter("page")
    val rpp     = request.optParameter("rpp")
  }

  def toMap = {
    val filtering = 
      params.filters.zip(Stream from 1).foldLeft(Seq.empty[(String, String)]) { (acc, row) =>
        acc ++ Seq( 
          (s"filter_field_${row._2}" -> row._1.field),
          (s"filter_type_${row._2}" -> row._1.`type`),
          (s"filter_value_${row._2}" -> row._1.value)
        )
      }.toMap
    Map("action" -> "search") ++ params.map ++ filtering
  }

  val discoveryConfig = SearchUtils.getDiscoveryConfiguration(dso)

  val query = utils.nonEmpty(params.query)

  val defaultFilters = discoveryConfig.getDefaultFilterQueries.asScala

  val userFilters = params.filters.map { f =>
    SearchUtils
      .getSearchService
      .toFilterQuery(context, f.field, f.`type`, f.value )
      .getFilterQuery
  }

  val sortConfig = Option(discoveryConfig.getSearchSortConfiguration)

  val sortBy = params.sortBy.orElse {
    for {
      sc <- sortConfig
      sf <- sc.getSortFields.asScala.find(_ == sc.getDefaultSort)
    } yield SearchUtils.getSearchService.toSortFieldIndex(sf.getMetadataField, sf.getType)
  }.getOrElse("score")

  val order = params.order.orElse {
    sortConfig.map(sc => sc.getDefaultSortOrder.toString)
  }

  val rpp = utils.toInt(params.rpp).getOrElse(discoveryConfig.getDefaultRpp)
  val page = utils.toInt(params.page).getOrElse(0)
  val offset = if (page > 1) (page - 1) * rpp else 0
  
  val facetsConfig = Option(discoveryConfig.getSidebarFacets)

  lazy val preparedQuery = {
    val preparedQuery = new DiscoverQuery
    query.foreach( preparedQuery.setQuery(_) )
    defaultFilters.foreach( preparedQuery.addFacetQuery )
    userFilters.foreach( preparedQuery.addFilterQueries(_) )
    if (order == Some("asc"))
      preparedQuery.setSortField(sortBy, SORT_ORDER.asc)
    else 
      preparedQuery.setSortField(sortBy, SORT_ORDER.desc)
    preparedQuery.setMaxResults(rpp)
    preparedQuery.setStart(offset)
    facetsConfig.foreach { facets =>
      preparedQuery.setFacetMinCount(1)
      facets.asScala.foreach { facet =>
        preparedQuery.addFacetField(new DiscoverFacetField(
          facet.getIndexFieldName,
          facet.getType,
          facet.getFacetLimit + 1 + userFilters.size, 
          facet.getSortOrder, 
          0
        ))
      }
    }
    preparedQuery
  }

  def filteringMap(
    ne: Option[Search.FilterEntry] = None, 
    de: Option[Search.FilterEntry] = None
  ) = {
    val map = mutable.Map("action" -> "search")

    val baseFilters = (de match {
      case Some(s) => params.filters.filter(_ != s)
      case _       => params.filters
    }).zip(Stream from 1)
    
    val newFilters = ne match {
      case Some(s) => Seq((s, 0))
      case _       => Nil
    }

    (baseFilters ++ newFilters).foreach { case (e, i) =>
      map(s"filter_field_$i") = e.field
      map(s"filter_type_$i")  = e.`type`
      map(s"filter_value_$i") = e.value
    }
    map.toMap
  }

  lazy val result = 
    SearchUtils.getSearchService.search(context, dso, preparedQuery)

  lazy val dspaceObjects = 
    result.getDspaceObjects.asScala.to[Seq]


  lazy val resultItems = dspaceObjects.collect { case i: Item => i }

  lazy val resultCommunities = dspaceObjects.collect { case c: Community => c }
  lazy val resultCollections = dspaceObjects.collect { case c: Collection => c }

  lazy val facetObjects = result.getFacetResults.asScala

  lazy val processedFacets = facetsConfig.map( facetsConf =>
    facetsConf.asScala.map { facetConfig =>
      val name = facetConfig.getIndexFieldName
      val optFacetResults = facetObjects.get(name).orElse(
        facetObjects.get(name + ".year")
      )
      optFacetResults.map { facetResults =>
        val limit = facetConfig.getFacetLimit + 1
        name -> facetResults.asScala.slice(0, limit).map { facetResult =>
          val tp = facetResult.getFilterType
          val fq = facetResult.getAsFilterQuery
          val dv = facetResult.getDisplayedValue
          val co = facetResult.getCount
          val wr = Search.FilterEntry(name, tp, fq)
          val is = params.filters.contains(wr)
          val map = if (is)
            filteringMap( de = Some(wr) )
          else
            filteringMap( ne = Some(wr) )
          ( is, dv, map, co )              
        }
      }
    }.flatten
  )
}