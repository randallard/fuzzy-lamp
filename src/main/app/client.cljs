(ns app.client
  (:require
    [com.fulcrologic.fulcro.react.version18 :refer [with-react18]]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defmutation make-blocked [{:space/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:space/id id :space/status] :blocked)))
(defmutation make-occupied [{:space/keys [id]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [:space/id id :space/status] :occupied)))
(defn space-css [type space-status]
  {:className (str "space "
                   (if (= space-status :occupied) "occupied ")
                   (if (= space-status :blocked) "blocked ")
                   (if (= type :row-type/goal) "goal "))})
(defsc OccupiedSteps [this {:occupied-step/keys [id player steps]}]
  {:query [:occupied-step/id :occupied-step/player :occupied-step/steps]
   :ident :occupied-step/id
   :initial-state {:occupied-step/id :param/id
                   :occupied-step/player :param/player
                   :occupied-step/steps :param/steps}}
  (dom/span {} (str "By " player " at " steps)))
(def ui-occupied-steps (comp/factory OccupiedSteps {:keyfn :occupied-step/id}))
(defsc Space [this {:space/keys [id number status occupied-steps]}]
  {:query [:space/id :space/number :space/status {:space/occupied-steps (comp/get-query OccupiedSteps)}]
   :ident :space/id
   :initial-state (fn [{:keys [id number status]}]
                    {:space/id id
                     :space/number number
                     :space/status status
                     :space/occupied-steps [(comp/get-initial-state OccupiedSteps {:id id
                                                                                   :player       :us
                                                                                   :steps        (cond (= status :occupied) [1]
                                                                                                       :else [])})]})}
  #_(dom/span (space-css nil status) "Space id " id " number " number " "
              (str status " ") (map ui-occupied-steps occupied))
  (dom/span (space-css nil status)
            (dom/button {:onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                        " block ")
            (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                        " move ")))
(def ui-space (comp/factory Space {:keyfn :space/id}))

(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident :row/id
   :initial-state (fn [{:keys [id number type]}]
                    {:row/id id
                     :row/number number
                     :row/type type
                     :row/spaces (cond (> id 3) [(comp/get-initial-state Space {:id (+ (* (- number 1) 3) 1)
                                                                 :number 1
                                                                 :status :free})
                                  (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 2)
                                                                 :number 2
                                                                 :status (cond (= number 1) :occupied
                                                                               :else :free)})
                                  (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 3)
                                                                 :number 3
                                                                 :status :free})]
                                       :else [(comp/get-initial-state Space {:id (+ (* (- number 1) 3) 1)
                                                                             :number 1
                                                                             :status :free})
                                              (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 2)
                                                                             :number 2
                                                                             :status (cond (= number 1) :occupied
                                                                                           :else :free)})])})}
  (dom/div {} (dom/div {:style {:padding "5px"}} (map ui-space spaces))))

(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Position [this {:position/keys [id row-number space-number]}]
  {:query [:position/id :position/row-number :position/space-number]
   :ident :position/id}
  (dom/span {} "player at ( row " row-number ", column " space-number " )"))
(def ui-position (comp/factory Position {:keyfn :position/id}))
(defsc Block [this {:block/keys [id row-number space-number]}]
  {:query [:block/id :block/row-number :block/space-number]
   :ident :block/id}
  (dom/span {} "block set at ( row " row-number ", column " space-number " )"))
(def ui-block (comp/factory Block {:keyfn :block/id}))
(defsc Step [this {:step/keys [id number position-space-id block-space-id]}]
  {:query [:step/id :step/number :step/position-space-id :step/block-space-id]
   :ident :step/id
   :initial-state {:step/id :param/id
                   :step/number :param/number
                   :step/position-space-id :param/position-space-id}}
  (dom/div {} "Step " number " player at space id " position-space-id ))
(def ui-step (comp/factory Step {:keyfn :step/id}))
(defsc Plan [this {:plan/keys [id number steps] :as props}]
  {:query         [:plan/id :plan/number {:plan/steps (comp/get-query Step)}]
   :ident         :plan/id
   :initial-state {:plan/id     :param/id
                   :plan/number :param/number
                   :plan/steps  [{:id 1
                                  :number 1
                                  :position-space-id 2}]
                   }
   }
  (dom/div {} (dom/div {} "Plan Number " number
                  (dom/ul {} (map ui-step steps)))))
(def ui-plan (comp/factory Plan {:keyfn :plan/id}))
(defsc Board [this {:board/keys [id size rows plans] :as props}]
  {:query [:board/id :board/size {:board/rows (comp/get-query Row)} {:board/plans (comp/get-query Plan)}]
   :ident :board/id
   :initial-state {:board/id :param/id
                   :board/size :param/size
                   :board/rows [{:id 7
                                 :number 4
                                 :type :row-type/goal}
                                {:id 6
                                 :number 3
                                 :type :row-type/space}
                                {:id 5
                                 :number 2
                                 :type :row-type/space}
                                {:id 4
                                 :number 1
                                 :type :row-type/space}
                                {:id 3
                                 :number 3
                                 :type :row-type/goal}
                                {:id 2
                                 :number 2
                                 :type :row-type/spaces}
                                {:id 1
                                 :number 1
                                 :type :row-type/spaces}]
                   :board/plans [{:id 1
                                 :number 1}]}}
  (dom/div {:style {:width "100%"}}
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Board [" id "] Size " size)
           (dom/div {} (map ui-row rows)))
    (dom/div {:style {:float "left" :padding "10px"}}
           (dom/h2 {} "Plans")
           (dom/div {} (map ui-plan plans)))))

(def ui-board (comp/factory Board {:keyfn :board/id}))

(defonce app (-> (app/fulcro-app) (with-react18)))
(def board-rows-size-3 [[:row/id 7]
                   [:row/id 6]
                   [:row/id 5]
                   [:row/id 4]])
(def board-rows-size-2 [[:row/id 3]
                        [:row/id 2]
                        [:row/id 1]])
(defmutation get-board-of-size-2 [{:board/keys [id]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [:board/id 1 :board/size] 2)
          (swap! state assoc-in [:board/id id :board/rows] board-rows-size-2)))
(defmutation get-board-of-size-3 [{:board/keys [id]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [:board/id 1 :board/size] 3)
          (swap! state assoc-in [:board/id id :board/rows] board-rows-size-3)))
(defsc Root [this {:root/keys [board]}]
  {:query [{:root/board (comp/get-query Board)}]
   :initial-state {:root/board {:id 1 :size 0}}}
  (dom/div {} (ui-board board))
  (cond (zero? (:board/size board))
        (dom/div {} "select board size: "
                 (dom/button {:onClick #(comp/transact!
                                          this [(get-board-of-size-2 {:board/id (:board/id board)})])
                              :style {:margin "0px 15px"}}  " 2 ")
                 (dom/button {:onClick #(comp/transact!
                                          this [(get-board-of-size-3 {:board/id (:board/id board)})])
                              :style {:margin "0px 15px"}}  " 3 "))
        :else (dom/div {} (ui-board board))))
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
  (swap! (::app/state-atom app) assoc-in [:occupied-step/id 1 :occupied-step/steps] [1 2])
  (app/current-state app)
  )