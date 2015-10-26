## Disco

A simple web frontend for DSpace.

This application uses:

* the DSpace API (5.x) to connect to a local DSpace repository
* Jersey JAX-RS for URL routing (and not much else)
* [Play Twirl](https://github.com/playframework/twirl) for HTML templating
* [xsbt-web-plugin](https://github.com/earldouglas/xwp-template) for running in development
* Bower and Gulp for frontend asset management

#### Getting started

Set an enviroment variable `DSPACE_DIR` to the location of your DSpace directory [dspace]. If you are using Oracle, also add a variable `DB_NAME=ORACLE`. 

Run `./sbt` and then do `container:start`. By default, the application will be accessible at http://localhost:3000. See the SBT manual and [xsbt-web-plugin](https://github.com/earldouglas/xwp-template) for additional SBT commands. 

You can also start a REPL by running `console`. Load the DSpace environment by running `utils.DSpace.start` inside the console.

#### Build assets

    cd extra/
    npm install
    bower install
    gulp

#### Packaging

Run `./sbt`, `package`, then copy the generated WAR file into your Tomcat `webapps` directory. Make sure you have set `DSPACE_DIR` somewhere Tomcat can see it (e.g., `tomcat.conf`).

#### Other notes

I have deliberately tried to avoid buying into the whole Jersey JAX-RS ecosystem (although much of `web.scala` is littered with Jersey specific code that should be abstracted out into a separate component). I initially wanted it to be more modular, so that it would be possible to switch out the routing component easily (e.g., to use something like [UrlRewriteFilter](http://tuckey.org/urlrewrite/) instead of Jersey). However, I'm not sure if this is the best design decision.

<hr/>

## Notes for [DSpace User Interface Prototype Challenge](https://wiki.duraspace.org/display/DSPACE/DSpace+UI+Prototype+Challenge)

#### Customization Capabilities

<ol>
  <li>
    Show or describe how an administrator would be able to easily adjust the site wide theme layout based on local design/needs. Specifically, show or describe how the following changes might be achieved:<br/>
    <ol style="list-style-type: lower-alpha !important;">
      <li>
        How would someone change the colors, fonts, sizes of the site? (e.g. css changes)<br/>
        <blockquote>Modify the LESS code in <code>extra/application.less</code> and run <code>(cd extra && gulp stylesheets)</code></blockquote>
      </li>
      <li>How would someone modify the sitewide header/footer? (e.g. to change logo, title, etc)</li>
      <li>How would someone adjust the navigation bar to appear on left or right?</li>
      <li>
        How would someone change the location of the breadcrumb trail (e.g. from header to footer)?<br/>
        <blockquote>Modify the template layout in <code>src/main/twirl/views/layout.scala.html</code>.</blockquote>
      </li>
      <li>
        How would someone display additional metadata fields on the Item view page? For example, displaying “dc.publisher” on the default Item view site wide?<br/>
        <blockquote>Modify the item view template in <code>src/main/twirl/views/item.scala.html</code>.</blockquote>
      </li>
      <li>
        How would someone add new links or an entire section to the navigation menu (e.g. links to other university pages)?<br/>
        <blockquote>Modify the template layout in <code>src/main/twirl/views/layout.scala.html</code>.</blockquote>
      </li>
    </ol>
  </li>
</ol>

#### Modularization Capabilities

<ol>
  <li>
    How could this UI platform support optional modules/features?<br/>
    <ol style="list-style-type: lower-alpha !important;">
      <li>For example, Embargo is an existing optional feature within DSpace. While it is disabled by default, many sites choose to enable it.</li>
      <li>Enabling Embargo functionality requires additional metadata fields to be captured in the deposit form (e.g. embargo date, embargo reason).</li>
      <li>
        Does this UI framework support the idea of easily enabling (or disabling) features that require UI-level changes? In other words, in this framework, do you feel enabling Embargo functionality could be automated via a configuration (e.g. embargo.enabled=true)? Or would it require manual editing of the deposit form to add additional metadata fields?<br/>
        <blockquote>Yes, this could be a configurable option; the deposit form template could have conditional statements that could collect/display embargo information or not.</blockquote>
      </li>
    </ol>
  </li>
  <li>
    How could this UI platform support new extensions/add-ons?<br/>
    <ol style="list-style-type: lower-alpha !important;">
      <li>
        Assume that someone has created a new Streaming feature for DSpace which provides a streaming capability for Video/Audio content. How might this UI platform support overriding/overlaying the default Item View to make that streaming feature available?<br/>
        <blockquote>The item view template could be customized to add the streaming feature, or could be configured to display the streaming feature when enabled.</blockquote>
      </li>
    </ol>
  </li>
</ol>

#### Prototype Documentation

<ol>
  <li>
    Describe the design of the prototype   (e.g. technologies/platforms used, including version of DSpace, etc.)?<br/>
    <blockquote>See notes above</blockquote>
  </li>
  <li>
    How do you install the prototype on a new system? (Note: we will be testing the installation of prototypes in order to evaluate the installation of the platform itself)<br/>
    <blockquote>See notes above</blockquote>
  </li>
  <li>
    How would you envision i18n (internationalization) support could be added to this UI prototype/platform in the future?<br/>
    <blockquote>By defining various <code>messages_XX.properties</code> files in <code>src/main/resources</code> and modifying the message display component to use the correct locale.</blockquote>
  </li>
  <li>
    How would you envision theming capabilities could be added to this UI prototype/platform in the future? In other words, how might local or third-party themes be installed or managed? Think of a theme as a collection of styles, fonts, logo, and page overrides.<br/>
    <blockquote>Style components are separated from the application code in the <code>extra</code> folder. Themes could be developed and copied over using all the functionality of Bower and Gulp.</blockquote>
  </li>
  <li>
    How would you envision supporting common DSpace authentication mechanisms (e.g. LDAP, Shibboleth) in this UI prototype/platform in the future?<br/>
    <blockquote>This is already built in via the DSpace API.</blockquote>
  </li>
</ol>
