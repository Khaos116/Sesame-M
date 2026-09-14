(function () {
    "use strict";
    // Read-only snapshot. Never wraps fetch/XHR, changes DOM, or reads request/response bodies.
    function hash(text) {
        var h = 2166136261;
        for (var i = 0; i < text.length; i++) {
            h ^= text.charCodeAt(i);
            h = Math.imul(h, 16777619);
        }
        return ("00000000" + (h >>> 0).toString(16)).slice(-8);
    }
    function pathOnly(url) { return String(url || "").split(/[?#]/)[0]; }
    var perf = window.performance;
    var text = document.body ? String(document.body.innerText || "").slice(0, 8192) : "";
    var images = [];
    var nodes = document.images || [];
    for (var i = 0; i < Math.min(nodes.length, 64); i++) {
        var node = nodes[i];
        var box = node.getBoundingClientRect();
        if (box.width >= 100 && box.height >= 60 && box.bottom > 0 && box.top < window.innerHeight) {
            images.push({key: hash(pathOnly(node.currentSrc || node.src)),
                         width: Math.round(box.width), height: Math.round(box.height),
                         complete: !!node.complete});
        }
        if (images.length >= 4) break;
    }
    var resources = [];
    var videos = [];
    var videoNodes = document.querySelectorAll ? document.querySelectorAll("video") : [];
    for (var k = 0; k < Math.min(videoNodes.length, 4); k++) {
        var video = videoNodes[k];
        videos.push({key: hash(pathOnly(video.currentSrc || video.src)),
            currentMs: Math.round(Number(video.currentTime || 0) * 1000),
            durationMs: Math.round(Number(video.duration || 0) * 1000),
            paused: !!video.paused, ended: !!video.ended});
    }
    var entries = perf && perf.getEntriesByType ? perf.getEntriesByType("resource") : [];
    for (var j = Math.max(0, entries.length - 32); j < entries.length; j++) {
        var e = entries[j];
        var path = pathOnly(e.name);
        var type = e.initiatorType;
        // Resource timing can be incomplete/cross-origin redacted. A candidate is NOT proof of submission.
        if (type !== "fetch" && type !== "xmlhttprequest") continue;
        resources.push({key: hash(path + "|" + e.startTime),
                        candidate: /captcha|verify|validate|checkcode/i.test(path),
                        startMs: Math.round(e.startTime), endMs: Math.round(e.responseEnd),
                        durationMs: Math.round(e.duration),
                        httpStatus: typeof e.responseStatus === "number" && e.responseStatus > 0
                            ? e.responseStatus : null});
    }
    return JSON.stringify({ready: document.readyState,
        pageKey: hash(pathOnly(window.location.href)),
        // Video-task binding: a bounded contentId token from the page URL query,
        // never the full URL (query may carry tokens) and never free text.
        contentId: (/(?:^|[?&])contentId=([A-Za-z0-9_.:\-]{1,256})(?:[&]|$)/
            .exec(String(window.location.search || "")) || [])[1] || null,
        // Bounded path-only source page for the watch-record payload.
        pagePath: (function (p) {
            p = String(p || "");
            return p.length >= 1 && p.length <= 256 ? p : null;
        })(pathOnly(window.location.href)),
        timeOrigin: perf && typeof perf.timeOrigin === "number" ? perf.timeOrigin : null,
        nowMs: perf && perf.now ? Math.round(perf.now()) : null,
        visible: document.visibilityState === "visible",
        failureText: /验证失败/.test(text), successText: /验证通过|验证成功/.test(text),
        loadingText: /加载中|验证中/.test(text),
        images: images, videos: videos, resources: resources.slice(-8),
        resourceTimingAvailable: !!(perf && perf.getEntriesByType)});
}());
