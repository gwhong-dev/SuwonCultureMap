// 가짜 카카오 SDK — 화면 시험에서 dapi.kakao.com(지도 SDK 로더) 대신 이 파일을 돌려줌 (support.js)
// 공유 SDK(t1.kakaocdn.net)는 index.html 의 integrity(SRI) 검사 때문에 가짜 파일을 못 넣어서 막고,
// 대신 여기서 window.Kakao 까지 같이 만든다 (지도 로더가 먼저 읽힘).
// 화면 코드가 쓰는 만큼만 흉내 냄: 지도는 그리지 않고 중심 · 레벨 · 이벤트만 기억한다.
// 새 카카오 기능을 화면에서 쓰기 시작하면 여기에도 같은 이름을 더해야 시험이 돈다.
(function () {
  if (window.kakao && window.kakao.maps) return;

  function LatLng(lat, lng) {
    this.lat = lat;
    this.lng = lng;
  }
  LatLng.prototype.getLat = function () {
    return this.lat;
  };
  LatLng.prototype.getLng = function () {
    return this.lng;
  };
  LatLng.prototype.equals = function (other) {
    return !!other && other.lat === this.lat && other.lng === this.lng;
  };
  LatLng.prototype.toString = function () {
    return "(" + this.lat + ", " + this.lng + ")";
  };

  function LatLngBounds(sw, ne) {
    this.sw = sw || null;
    this.ne = ne || null;
  }
  LatLngBounds.prototype.extend = function (p) {
    if (!this.sw) {
      this.sw = new LatLng(p.lat, p.lng);
      this.ne = new LatLng(p.lat, p.lng);
      return;
    }
    this.sw = new LatLng(Math.min(this.sw.lat, p.lat), Math.min(this.sw.lng, p.lng));
    this.ne = new LatLng(Math.max(this.ne.lat, p.lat), Math.max(this.ne.lng, p.lng));
  };
  LatLngBounds.prototype.contain = function (p) {
    return !!this.sw && p.lat >= this.sw.lat && p.lat <= this.ne.lat && p.lng >= this.sw.lng && p.lng <= this.ne.lng;
  };
  LatLngBounds.prototype.getSouthWest = function () {
    return this.sw;
  };
  LatLngBounds.prototype.getNorthEast = function () {
    return this.ne;
  };
  LatLngBounds.prototype.isEmpty = function () {
    return !this.sw;
  };

  // 이벤트: 대상 객체마다 이름별 함수 목록
  var listeners = new WeakMap();
  function listenersOf(target, type) {
    var all = listeners.get(target);
    if (!all) listeners.set(target, (all = {}));
    return all[type] || (all[type] = []);
  }
  function fire(target, type) {
    listenersOf(target, type).slice().forEach(function (fn) {
      fn();
    });
  }

  function Map(container, options) {
    this.container = container;
    this.center = options.center;
    this.level = options.level || 3;
  }
  Map.prototype.setCenter = function (p) {
    this.center = p;
    fire(this, "center_changed");
    fire(this, "idle");
  };
  Map.prototype.panTo = Map.prototype.setCenter;
  Map.prototype.getCenter = function () {
    return this.center;
  };
  Map.prototype.setLevel = function (level) {
    if (level === this.level) return;
    this.level = level;
    fire(this, "zoom_changed");
    fire(this, "idle");
  };
  Map.prototype.getLevel = function () {
    return this.level;
  };
  // 시험에선 화면 크기와 상관없이 수원 전체가 보인다고 봄 (지도 범위로 거르는 목록이 비지 않게)
  Map.prototype.getBounds = function () {
    return new LatLngBounds(new LatLng(36.5, 126.0), new LatLng(38.5, 128.0));
  };
  Map.prototype.setBounds = function (bounds) {
    if (bounds && bounds.sw) {
      this.setCenter(new LatLng((bounds.sw.lat + bounds.ne.lat) / 2, (bounds.sw.lng + bounds.ne.lng) / 2));
    }
  };
  Map.prototype.relayout = function () {};
  Map.prototype.setDraggable = function () {};
  Map.prototype.setZoomable = function () {};

  function Marker(options) {
    this.position = options.position;
    this.map = options.map || null;
  }
  Marker.prototype.setMap = function (map) {
    this.map = map;
  };
  Marker.prototype.getMap = function () {
    return this.map;
  };
  Marker.prototype.getPosition = function () {
    return this.position;
  };
  Marker.prototype.setPosition = function (p) {
    this.position = p;
  };
  Marker.prototype.setImage = function () {};
  Marker.prototype.setOpacity = function () {};
  Marker.prototype.setZIndex = function () {};

  function CustomOverlay(options) {
    this.content = options.content;
    this.position = options.position;
    this.map = options.map || null;
  }
  CustomOverlay.prototype.setMap = function (map) {
    this.map = map;
  };
  CustomOverlay.prototype.getMap = function () {
    return this.map;
  };
  CustomOverlay.prototype.setPosition = function (p) {
    this.position = p;
  };
  CustomOverlay.prototype.setContent = function (c) {
    this.content = c;
  };
  CustomOverlay.prototype.setZIndex = function () {};

  window.kakao = {
    maps: {
      // 진짜 로더처럼 콜백은 나중에 (index.js 가 load 를 부른 뒤의 코드까지 다 읽힌 다음)
      load: function (callback) {
        setTimeout(callback, 0);
      },
      LatLng: LatLng,
      LatLngBounds: LatLngBounds,
      Map: Map,
      Marker: Marker,
      MarkerImage: function MarkerImage(src, size, options) {
        this.src = src;
        this.size = size;
        this.options = options;
      },
      Size: function Size(w, h) {
        this.width = w;
        this.height = h;
      },
      Point: function Point(x, y) {
        this.x = x;
        this.y = y;
      },
      CustomOverlay: CustomOverlay,
      StaticMap: function StaticMap() {},
      event: {
        addListener: function (target, type, fn) {
          listenersOf(target, type).push(fn);
        },
        removeListener: function (target, type, fn) {
          var list = listenersOf(target, type);
          var i = list.indexOf(fn);
          if (i !== -1) list.splice(i, 1);
        },
        trigger: function (target, type) {
          fire(target, type);
        },
      },
    },
  };

  // 카카오톡 공유 SDK 자리 — 보낸 내용만 기억
  var initialized = false;
  window.Kakao = {
    init: function () {
      initialized = true;
    },
    isInitialized: function () {
      return initialized;
    },
    Share: {
      sent: [],
      sendDefault: function (options) {
        window.Kakao.Share.sent.push(options);
      },
    },
  };
})();
