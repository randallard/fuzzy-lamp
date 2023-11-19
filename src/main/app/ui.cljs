(ns app.ui
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))


(defmutation make-blocked [{:space/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:space/id id :space/occupant] :blocker)
                     (let [player-space-id (get-in @state [:state-data/id :board :state-data/player-space-id])
                           old-player-space-steps (get-in @state [:space/id player-space-id :space/occupied-steps])
                           active-board-id (get-in @state [:state-data/id :board :state-data/active-board-id])
                           old-steps (get-in @state [:space/id id :space/occupied-steps])
                           new-step-number (inc (get-in @state [:board/id active-board-id :board/step-number]))
                           new-steps (conj old-steps new-step-number)
                           new-player-space-steps (conj old-player-space-steps new-step-number)]
                       (swap! state assoc-in [:space/id player-space-id :space/occupied-steps] new-player-space-steps)
                       (swap! state assoc-in [:space/id id :space/occupied-steps] new-steps)
                       (swap! state assoc-in [:board/id active-board-id :board/step-number] new-step-number))))
(defmutation make-occupied [{:keys [space/id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:space/id id :space/occupant] :player)
                     (let [active-board-id (get-in @state [:state-data/id :board :state-data/active-board-id])
                           old-steps (get-in @state [:space/id id :space/occupied-steps])
                           new-step-number (inc (get-in @state [:board/id active-board-id :board/step-number]))
                           new-steps (conj old-steps new-step-number)]
                       (swap! state assoc-in [:state-data/id :board :state-data/player-space-id] id)
                       (swap! state assoc-in [:space/id id :space/occupied-steps] new-steps)
                       (swap! state assoc-in [:board/id active-board-id :board/step-number] new-step-number))))
(defn space-css [type occupant]
  {:className (str "space "
                   (if (= occupant :player) "occupied ")
                   (if (= occupant :blocker) "blocked ")
                   (if (= type :row-type/goal) "goal "))})

(defsc Space [this {:space/keys [id number occupant occupied-steps]} {:keys [onSpaceStep]}]
       {:query [:space/id :space/number :space/occupant :space/occupied-steps]
        :ident :space/id
        :initial-state (fn [{:keys [id number occupant]}]
                         {:space/id id
                          :space/number number
                          :space/occupant occupant
                          :space/occupied-steps (cond (= occupant :player) [1]
                                                      :else [])})}
       (dom/span (space-css nil occupant) "Space " id " steps " (str occupied-steps " " occupant)
                 (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                             " move ")
                 (dom/button {:onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                             " block ")))
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
                                                                                     :occupant nil})
                                                      (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 2)
                                                                                     :number 2
                                                                                     :occupant (cond (= number 1) :player
                                                                                                   :else nil)})
                                                      (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 3)
                                                                                     :number 3
                                                                                     :occupant nil})]
                                            :else [(comp/get-initial-state Space {:id (+ (* (- number 1) 3) 1)
                                                                                  :number 1
                                                                                  :occupant nil})
                                                   (comp/get-initial-state Space {:id (+ (* (- number 1) 3) 2)
                                                                                  :number 2
                                                                                  :occupant (cond (= number 1) :player
                                                                                                :else nil)})])})}
       (dom/div {} (dom/div {:style {:padding "5px"}} (map ui-space spaces))))

(def ui-row (comp/factory Row {:keyfn :row/id}))
(defsc Board [this {:board/keys [id size rows step-number] :as props}]
       {:query [:board/id :board/size :board/step-number {:board/rows (comp/get-query Row)}]
        :ident :board/id
        :initial-state {:board/id :param/id
                        :board/size :param/size
                        :board/step-number :param/step-number
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
                                      :type :row-type/spaces}]}}
       (dom/div {:style {:width "100%"}}
                (dom/div {:style {:float "left" :padding "10px"}}
                         (dom/h2 {} "Board [" id "] Size " size)
                         (dom/div {} (map ui-row rows)))))

(def ui-board (comp/factory Board {:keyfn :board/id}))

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
                     (swap! state assoc-in [:board/id id :board/rows] board-rows-size-2)
                     (swap! state assoc-in [:state-data/id :board :state-data/state] :planning)))
(defmutation get-board-of-size-3 [{:board/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:board/id 1 :board/size] 3)
                     (swap! state assoc-in [:board/id id :board/rows] board-rows-size-3)
                     (swap! state assoc-in [:state-data/id :board :state-data/state] :planning)))
(defsc StateData [this {:state-data/keys [id active-board-id player-space-id size state] :as props}]
  {:query [:state-data/id :state-data/active-board-id :state-data/player-space-id :state-data/size :state-data/state]
   :ident :state-data/id
   :initial-state {:state-data/id :param/id
                   :state-data/active-board-id :param/active-board-id
                   :state-data/player-space-id :param/player-space-id
                   :state-data/size :param/size
                   :state-data/state :param/state}}
  (dom/div {} (dom/p {} "state data " (str id " size " size " state " state " active board id " active-board-id " current player space " player-space-id)) ))
(def ui-state-data (comp/factory StateData {:keyfn :state-data/id}))
(defsc Root [this {:root/keys [board state-data]}]
       {:query [{:root/board (comp/get-query Board)} {:root/state-data (comp/get-query StateData)}]
        :initial-state {:root/board {:id 1 :size 0 :step-number 1}
                        :root/state-data {:id :board :size 0 :active-board-id 1 :player-space-id 2 :state :choose-size}}}
  (dom/div {}
           (ui-state-data state-data)
           (cond (= (:state-data/state state-data) :choose-size)
                 (dom/div {} "select board size: "
                          (dom/button {:onClick #(comp/transact!
                                                   this [(get-board-of-size-2 {:board/id (:board/id board)})])
                                       :style {:margin "0px 15px"}}  " 2 ")
                          (dom/button {:onClick #(comp/transact!
                                                   this [(get-board-of-size-3 {:board/id (:board/id board)})])
                                       :style {:margin "0px 15px"}}  " 3 "))
                 :else (ui-board board))))
