var gulp    = require('gulp');
var uglify  = require('gulp-uglify');
var rename  = require('gulp-rename');
var concat  = require('gulp-concat');
var coffee  = require('gulp-coffee');
var less    = require('gulp-less');
var streamqueue = require('streamqueue');

gulp.task('scripts', function() {  
  var application = gulp.src('application.coffee')
    .pipe(coffee({bare: true}));
    
  var others = gulp.src([
    'bower_components/jquery/dist/jquery.js',
    'bower_components/bootstrap/dist/js/bootstrap.js'
  ]);

  streamqueue({ objectMode: true }, others, application)
    .pipe(concat('application.min.js'))
    .pipe(uglify())
    .pipe(gulp.dest('../src/main/webapp/static'));

});

gulp.task('stylesheets', function () {
  var lessOpts = {
    compress: true,
    paths: [ 'bower_components']
  };

  gulp.src('application.less')
    .pipe(less(lessOpts))
    .pipe(rename('application.min.css'))
    .pipe(gulp.dest('../src/main/webapp/static'));


});

gulp.task('fonts', function() {
  gulp.src([
    'bower_components/bootstrap/fonts/*',
    'bower_components/font-awesome/fonts/*'
  ])
  .pipe(gulp.dest('../src/main/webapp/static/fonts'));
});

gulp.task('images', function() {
  gulp.src(['images/*'])
    .pipe(gulp.dest('../src/main/webapp/static/images'));
});

gulp.task('default', ['scripts', 'stylesheets']);

// gulp.task('default', function() {
//   // place code for your default task here
// });