(ns kahuin.p2p.transport
  (:require
    [goog.object :as gobj]
    [shadow.js :as shadow-js]
    ["libp2p-webrtc-star" :as WebRTCStar]))

; Monkey-patch shadow.js/process to implement nextTick, needed by WebRTCStar implementation
(gobj/set shadow-js/process "nextTick" goog.async.nextTick)

(defn- id->address
  [id]
  (str "/ip4/0.0.0.0/tcp/9090/ws/p2p-webrtc-star/p2p/" id))

(defn init
  [id]
  (let [wstar (WebRTCStar.)]
    {::address (id->address id)
     :modules {:transport [wstar]
               :peerDiscovery [(.-discovery wstar)]}
     :config {}}))