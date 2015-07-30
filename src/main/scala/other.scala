package org.dspace.utils {
  class DSpaceWebapp extends org.dspace.app.util.AbstractDSpaceWebapp("DISCO") {
    def isUI = true
  }
}

package views.html {
  object static {
    def siteName = "Repository"
  }  
}

package io.github.kardeiz.disco {
  package web {
    class Init extends javax.servlet.ServletContextListener {
      def contextDestroyed(sce: javax.servlet.ServletContextEvent) {}

      def contextInitialized(sce: javax.servlet.ServletContextEvent) {
        val sc = sce.getServletContext
        sc.setInitParameter("dspace.dir", utils.config.dspaceDir)
        sc.setInitParameter("dspace-config", utils.config.dspaceCfg)
      }
    }
  }  
}