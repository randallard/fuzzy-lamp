# Getting set up in IntelliJ Idea

You should have Cursive installed in IntelliJ

Start by setting deps.edn to open in IntelliJ - for example, in Windows file explorer, right click deps.edn and choose 'open with' then 'choose another application'.  
Check the box next to "Always use this app to open .edn files" and select IntelliJ Idea 

Open IntelliJ Idea by double clicking deps.edn

Find and click the Terminal tab at the bottom of IntelliJ Idea
cd to your repo, probably fulcro-developers-guide-start
run shadow-cljs install with the following command

    npm install shadow-cljs react react-dom --save

run in the terminal

    npx shadow-cljs server

in a browser, go to the localhost:9### listed (where :9### is maybe :9630)

click 'Builds' in the top tabs list

click on the word 'main' so you can see the updates as it compiles, text in a box labeled 'Status' will appear

click 'Watch'
wait for green

in a new tab, go to http://localhost:8000

Should see TODO

If you have trouble with the following steps - refer to the instructions at: https://cursive-ide.com/userguide/deps.html

in IntelliJ
Edit Configurations
add (+) Clojure REPL - Remote
set the following
name: cljs repl
connection type:  nREPL
connection details:
- connect to server
  host: localhost
  port: 9000
  click 'apply' then 'ok'

  Choose 'cljs repl'
  click the green play button next to it
  click in the repl where you see "Connecting to remote nREPL..."
  enter the following (it will show up below)
  (+ 1 1)
  submit with ctrl+enter
  hopefully you see => 2 show up in the REPL
  run
  (shadow/repl :main)
  run
  (js/alert "howdy")
  go to your localhost:8000 tab and you should see the alert "howdy"
  click ok on that
  open dev tools there in chrome and go to the console
  back in the repl, run
  (print "up and running!")
  in the console of the browser you should see "up and running!"
  in IntelliJ, open /src/main/app/client.cljs
  add the following to the bottom of that file:
  (defn f [x] (* x x))
  in the console, run
  app.client.f(3)
  in the repl you need to switch to the namespace with
  (in-ns 'app.client)
  maybe you can run
  (f 3)
