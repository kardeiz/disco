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

Run `./sbt` and then do `container:start`. See the SBT manual and [xsbt-web-plugin](https://github.com/earldouglas/xwp-template) for additional SBT commands. 

You can also start a REPL by running `console`. Load the DSpace environment by running `utils.DSpace.start`.

#### Build assets

    cd extra/
    npm install
    bower install
    gulp

#### Packaging

Run `./sbt`, `package`, then copy the generated WAR file into your Tomcat `webapps` directory. Make sure you have set `DSPACE_DIR` somewhere Tomcat can see it (e.g., `tomcat.conf`).
