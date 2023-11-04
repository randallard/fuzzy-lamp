(ns app.client
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defsc Space [this {:space/keys [id row column blocked occupied]}]
  {}
  (dom/div "Space[" id "] (" column "," row ")"))
(def ui-space (comp/factory Space {:keyfn :space/id}))
(defsc Board [this {:board/keys [id size] :as props}]
  {:query [:board/id :board/size]
   :ident :board/id}
  (dom/div (dom/h2 "Board [" id "] Size " size)))

(def ui-board (comp/factory Board {:keyfn :board/id}))

(defonce app (-> (app/fulcro-app) (with-react18)))

(defsc Root [this {:keys [root]}]
  {}
  (dom/div (ui-board root)))

(defn ^:export init
  "Shadow-cljs sets this up to be our entry-point function. See shadow-cljs.edn `:init-fn` in the modules of the main build."
  []
  (app/mount! app Root "app")
  (js/console.log "Loaded"))

(defn ^:export refresh
  "During development, shadow-cljs will call this on every hot reload of source. See shadow-cljs.edn"
  []
  ;; re-mounting will cause forced UI refresh, update internals, etc.
  (app/mount! app Root "app")
  ;; As of Fulcro 3.3.0, this addition will help with stale queries when using dynamic routing:
  (comp/refresh-dynamic-queries! app)
  (js/console.log "Hot reload"))

(comment
  (app/current-state app)
  (merge/merge-component! app Board {:board/id 1
                                     :board/size 3
                                     })
  (app/schedule-render! app)
  (reset! (::app/state-atom app) {:root {:board/id 1
                                         :board/size 3
                                         }})
  )