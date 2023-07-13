# fulcro-developers-guide-start
use this to skip to the good part of the fulcro developers guide 

set deps.edn to open with IntelliJ
double click deps.edn
install deps as described here: https://cursive-ide.com/userguide/deps.html (must use v 0.12.1090 - tried through 0.12.1109 and they dont work)

run shadow-cljs install with the following command
npm install shadow-cljs react react-dom --save
run in the terminal
npx shadow-cljs server
browse to the localhost:9### listed

click 'Builds'
click 'Watch'
wait for green

click 'Dashboard'
click localhost:8000

Should see TODO

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
  (println "up and running!")
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

Going remote - need to  

npm install shadow-cljs react react-dom --save
npx shadow-cljs server

need to run (in-ns 'user) then (start)