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
                     (let [active-board-id (get-in @state [:state-data/id :board :state-data/active-board-id])]
                         (let [old-steps (get-in @state [:space/id id :space/occupied-steps])
                               new-step-number (inc (get-in @state [:board/id active-board-id :board/step-number]))
                               new-steps (conj old-steps new-step-number)]
                           (swap! state assoc-in [:state-data/id :board :state-data/player-space-id] id)
                           (swap! state assoc-in [:space/id id :space/occupied-steps] new-steps)
                           (swap! state assoc-in [:board/id active-board-id :board/step-number] new-step-number))
                         ; reset show-move-block-button for each space
                         (let [clicked-space-row (get-in @state [:space/id id :space/row])
                               clicked-space-number (get-in @state [:space/id id :space/number])
                               rows (get-in @state [:board/id active-board-id :board/rows])
                               new-state (map (fn [row]
                                             (let [row-spaces (get-in @state (conj row :row/spaces))
                                                   row-number (get-in @state (conj row :row/number))]
                                               (vec (map (fn [row-space] #_(str " row space " row-space
                                                                             " row number" row-number
                                                                             " space number " space-number)
                                                           (let [space-number (get-in @state (conj row-space :space/number))
                                                                 is-in-same-row (= clicked-space-row row-number)
                                                                 is-one-space-away (or (= 1 (- clicked-space-number space-number))
                                                                                       (= 1 (- space-number clicked-space-number)))
                                                                 is-one-row-away (or (= 1 (- clicked-space-row row-number))
                                                                                     (= 1 (- row-number clicked-space-row)))
                                                                 is-same-space-number (= clicked-space-number space-number)
                                                                 is-adjascent (cond (and is-in-same-row is-one-space-away) true
                                                                                    (and is-one-row-away is-same-space-number) true
                                                                                    :else false)]
                                                             (swap! state assoc-in (conj row-space :space/show-move-block-button)
                                                                    is-adjascent )))
                                                         row-spaces)))) rows)]
                           (print (str "new state " new-state))
                           ))))
(defn space-css [type occupant]
  {:className (str "space "
                   (if (= occupant :player) "occupied ")
                   (if (= occupant :blocker) "blocked ")
                   (if (= type :row-type/goal) "goal "))})

(defsc Space [this {:space/keys [id number occupant occupied-steps show-move-block-button row]}]
       {:query [:space/id :space/number :space/row :space/occupant :space/occupied-steps :space/show-move-block-button]
        :ident :space/id
        :initial-state (fn [{:keys [id number occupant show-move-block-button row]}]
                         {:space/id id
                          :space/show-move-block-button show-move-block-button
                          :space/number number
                          :space/row row
                          :space/occupant occupant
                          :space/occupied-steps (cond (= occupant :player) [1]
                                                      :else [])})}
       (dom/span (space-css nil occupant)
                 (if (true? show-move-block-button) (dom/button {:onClick #(comp/transact! this [(make-occupied {:space/id id})]) :style {:margin "0px 15px"}}
                             " move "))
                 (if (true? show-move-block-button) (dom/button {:onClick #(comp/transact! this [(make-blocked {:space/id id})]) :style {:margin "0px 15px"}}
                             " block "))))
(def ui-space (comp/factory Space {:keyfn :space/id}))

(defsc Row [this {:row/keys [id number type spaces] :as props}]
  {:query         [:row/id :row/number :row/type {:row/spaces (comp/get-query Space)}]
   :ident         :row/id
   :initial-state (fn [{:keys [id number type]}]
                    {:row/id     id
                     :row/number number
                     :row/type   type
                     :row/spaces (let [row-number number] (cond (> id 3) [(comp/get-initial-state Space {
                                                                                :id             (+ (* (- row-number 1) 3) 1)
                                                                                :number         1
                                                                                :row row-number
                                                                                :occupant       nil
                                                                                :show-move-block-button (cond (= row-number 1) true
                                                                                                              :else false)
                                                                                })
                                                 (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 2)
                                                                                :number   2
                                                                                :row row-number
                                                                                :show-move-block-button (cond (= row-number 2) true
                                                                                                              :else false)
                                                                                :occupant (cond (= row-number 1) :player
                                                                                                :else nil)})
                                                 (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 3)
                                                                                :number   3
                                                                                :row row-number
                                                                                :show-move-block-button (cond (= row-number 1) true
                                                                                                              :else false)
                                                                                :occupant nil})]
                                       :else [(comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 1)
                                                                             :number   1
                                                                             :row row-number
                                                                             :show-move-block-button (cond (= row-number 1) true
                                                                                                           :else false)
                                                                             :occupant nil})
                                              (comp/get-initial-state Space {:id       (+ (* (- row-number 1) 3) 2)
                                                                             :number   2
                                                                             :row row-number
                                                                             :show-move-block-button (cond (= row-number 2) true
                                                                                                           :else false)
                                                                             :occupant (cond (= row-number 1) :player
                                                                                             :else nil)})]))})}
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
                     (swap! state assoc-in [:state-data/id :board :state-data/size] 2)
                     (swap! state assoc-in [:board/id 1 :board/size] 2)
                     (swap! state assoc-in [:board/id id :board/rows] board-rows-size-2)
                     (swap! state assoc-in [:state-data/id :board :state-data/state] :planning)))
(defmutation get-board-of-size-3 [{:board/keys [id]}]
             (action [{:keys [state]}]
                     (swap! state assoc-in [:state-data/id :board :state-data/size] 3)
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
           (let [get-new-board-button (fn [{:keys [size label]}]
                                        (print "get new board size " size " label " label)
                                        (dom/button {:onClick #(comp/transact!
                                                             this [(cond (= size 2) (get-board-of-size-2 {:board/id (:board/id board)})
                                                                         (= size 3) (get-board-of-size-3 {:board/id (:board/id board)}))])
                                                 :style {:margin "0px 15px"}}
                                                                   (str " " (cond (nil? label) size
                                                                                  :else label) " ")))]
             (cond (= (:state-data/state state-data) :choose-size)
                 (dom/div {} "select board size: "
                          (get-new-board-button {:size 2})
                          (get-new-board-button {:size 3}))
                 :else (dom/div
                         (get-new-board-button {:size (:state-data/size state-data) :label "clear board"})
                         (ui-board board))))))
