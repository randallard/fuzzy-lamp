(ns app.client
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defmutation block-here [{:space/keys [id]}]
  ; set :space/status to :blocked
  (action [{:keys [state]}]
          (swap! state assoc-in [:space/id id :space/status] :blocked))
  ; add this step number to :space/occupied vector
  ; add this :step to plan with :step/id,:step/number, [:space/id id] in :blocks vector, [:space/id current-position-space-id] in :step/positions vector
  )
(defmutation move-here [{:space/keys [id]}]
  ; set :space/status to :occupied
  (action [{:keys [state]}]
          (swap! state assoc-in [:space/id id :space/status] :occupied))
  ; add this step number to :space/occupied vector
  ; add this :step to plan with :step/id,:step/number, [:space/id id] in :step/positions vector
  )
(defn space-css [type space-status]
  {:className (str "space "
                   (if (= space-status :occupied) "occupied ")
                   (if (= space-status :blocked) "blocked ")
                   (if (= type :row-type/goal) "goal "))})
(defsc Occupied [this {:occupied/keys [id player steps]}]
  {:query [:occupied/id :occupied/player :occupied/steps]
   :ident :occupied/id}
  (dom/span {} (str "By " player " at " steps)))
(def ui-occupied (comp/factory Occupied {:keyfn :occupied/id}))
(defsc Space [this {:space/keys [id number status occupied]}]
  {:query [:space/id :space/number :space/status {:space/occupied (comp/get-query Occupied)}]
   :ident :space/id}
  #_(dom/span (space-css nil status) "Space id " id " number " number " "
            (str status " ") (map ui-occupied occupied) )
  (dom/span (space-css nil status)
            (dom/button {:onClick #(comp/transact! this [(block-here {:space/id id})]) :style {:margin "0px 15px"}}
                        " block ")
            (dom/button {:onClick #(comp/transact! this [(move-here {:space/id id})]) :style {:margin "0px 15px"}}
                        " move ")))
(def ui-space (comp/factory Space {:keyfn :space/id}))

(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident :row/id}
  (dom/div {} #_(str "Row[" number "] type " type)
           (dom/div {:style {:padding "5px"}}
                    (cond
                      (= type :row-type/goal) (dom/span "GOAL--->GOAL--->GOAL-->" (map ui-space spaces) "<---GOAL<---GOAL---GOAL")
                      (= type :row-type/spaces) (map ui-space spaces)))))

(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Step [this {:step/keys [id number position-space block-space]}]
  {:query [:step/id :step/number :step/block-space :step/position-space]
   :ident :step/id}
  (dom/div {} "Step " number " " (cond (nil? block-space) (str " move to space id " (:id position-space))
                                       :else (str " block space id " (:id block-space)
                                                  " from position space id " (:id position-space)))))
(def ui-step (comp/factory Step {:keyfn :step/id}))
(defsc Plan [this {:plan/keys [id number steps] :as props}]
  {:query [:plan/id :plan/number {:plan/steps (comp/get-query Step)}]
   :ident :plan/id}
  (dom/div {} (dom/p {} "Plan Number " number
                  (dom/ul {} (map ui-step steps)))))
(def ui-plan (comp/factory Plan {:keyfn :plan/id}))
(defsc Board [this {:board/keys [id size rows plans] :as props}]
  {:query [:board/id :board/size {:board/rows (comp/get-query Row)} {:board/plans (comp/get-query Plan)}]
   :ident :board/id}
  (dom/div {:style {:width "100%"}}
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Board [" id "] Size " size)
           (dom/div {} (map ui-row rows)))
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Plans")
           (dom/div {} (map ui-plan plans)))))

(def ui-board (comp/factory Board {:keyfn :board/id}))

(defonce app (-> (app/fulcro-app) (with-react18)))

(defsc Root [this {:root/keys [board]}]
  {:query [{:root/board (comp/get-query Board)}]
   :initial-state {:root/board {:id 1 :size 3}}}
  (dom/div {} (ui-board board)))

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
  (swap! (::app/state-atom app) assoc-in [:occupied/id 1 :occupied/steps] [1 2])
  (app/current-state app)
  )