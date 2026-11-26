package com.project.lol.webview.injections

object FetchOverride {
    const val CONTENT = """
            (function(){
                if(window.oriFetch) return;
                window.splOpHashes = window.splOpHashes || {};
                window.__splLocalDevId = window.__splLocalDevId || null;
                window.__splActiveDevId = window.__splActiveDevId || null;
                window.__splRemoteActive = function(){
                    try {
                        return !!(window.__splLocalDevId && window.__splActiveDevId && window.__splActiveDevId !== window.__splLocalDevId);
                    } catch(e){ return false; }
                };
                var orig = window.fetch.bind(window);
                window.oriFetch = orig;
                /* Every GraphQL answer the page receives, by fetch or XHR, lands here.
                   Data is recognised by its shape, not by operation name, so renamed or
                   new operations keep working. Consumers wait with splGqlWait. */
                window.__splGql = window.__splGql || {};
                window.__splGqlWaiters = window.__splGqlWaiters || [];
                window.splGqlPublish = function(op, j){
                    try {
                        var d = j && j.data;
                        if(!d || typeof d !== 'object') return;
                        var keys = Object.keys(d);
                        var key = op || ('?' + keys.join('+'));
                        if(!window.__splGql[key]) { try { AndBridge.dbg('v', 'gql seen: ' + key + ' -> ' + keys.join(',')); } catch(e){} }
                        window.__splGql[key] = { at: Date.now(), data: d };
                        if(d.home && (d.home.sectionContainer || d.home.greeting)) { window.__splHome = d.home; window.__splHomeAt = Date.now(); }
                        var ws = window.__splGqlWaiters;
                        for(var i = ws.length - 1; i >= 0; i--) { try { if(ws[i].test(d)) { ws[i].done(d); ws.splice(i, 1); } } catch(e){} }
                    } catch(e){}
                };
                /* Resolves with the first GraphQL data (already seen after `since`, or arriving later) that `test` accepts. */
                window.splGqlWait = function(test, since, ms){
                    return new Promise(function(resolve, reject){
                        var g = window.__splGql;
                        for(var k in g) { try { if(g[k].at > (since || 0) && test(g[k].data)) return resolve(g[k].data); } catch(e){} }
                        var w = { test: test, done: function(d){ clearTimeout(t); resolve(d); } };
                        var t = setTimeout(function(){ var i = window.__splGqlWaiters.indexOf(w); if(i >= 0) window.__splGqlWaiters.splice(i, 1); reject(new Error('no data from the page')); }, ms || 20000);
                        window.__splGqlWaiters.push(w);
                    });
                };
                (function(){
                    var X = window.XMLHttpRequest; if(!X || X.prototype.__splHooked) return;
                    X.prototype.__splHooked = true;
                    var oOpen = X.prototype.open, oSend = X.prototype.send;
                    X.prototype.open = function(m, u){ this.__splUrl = String(u || ''); return oOpen.apply(this, arguments); };
                    X.prototype.send = function(body){
                        var x = this;
                        if(x.__splUrl.indexOf('pathfinder') !== -1) {
                            var op = null; try { op = JSON.parse(body).operationName; } catch(e){}
                            x.addEventListener('load', function(){ try { window.splGqlPublish(op, typeof x.response === 'object' && x.response ? x.response : JSON.parse(x.responseText)); } catch(e){} });
                        }
                        return oSend.apply(this, arguments);
                    };
                })();
                window.fetch = function(input, init) {
                    if(init && init.headers) {
                        var h = init.headers;
                        var auth, cliTok;
                        if(typeof h.get === 'function') {
                            auth = h.get('Authorization') || h.get('authorization');
                            cliTok = h.get('Client-Token') || h.get('client-token');
                        } else {
                            auth = h.Authorization || h.authorization;
                            cliTok = h['Client-Token'] || h['client-token'];
                        }
                        if(auth && typeof auth === 'string') {
                            window.spotAuthToken = auth.indexOf('Bearer ')===-1 ? 'Bearer '+auth.replace(/^Bearer\s+/i,'') : auth;
                        }
                        if(cliTok) {
                            window.spotCliToken = cliTok;
                        }
                    }
                    var url = typeof input==='string' ? input : (input ? input.url : '');
                    if(url && url.indexOf) {
                        var m = url.match(/\/from\/([A-Za-z0-9_-]+)\/to\/([A-Za-z0-9_-]+)/);
                        if(m && m[1]) window.spotDevId = m[1];
                        if(m && m[1] && !window.__splLocalDevId) window.__splLocalDevId = m[1];
                        if(m && m[2]) window.__splActiveDevId = m[2];
                        var m2 = url.match(/connect-state\/v1\/player\/(?:command|transfer)\/from\/([A-Za-z0-9_-]+)\/to\/([A-Za-z0-9_-]+)/);
                        if(m2 && m2[2]) { window.spotDevId = m2[2]; window.__splActiveDevId = m2[2]; }
                        var m3 = url.match(/\/track-playback\/v1\/devices/);
                        if(m3 && init && init.body) {
                            try {
                                var pb = typeof init.body==='string' ? JSON.parse(init.body) : init.body;
                                if(pb && pb.device && pb.device.device_id) {
                                    if(!window.__splLocalDevId) window.__splLocalDevId = pb.device.device_id;
                                    if(pb.device.device_id!==window.spotDevId) window.spotDevId = pb.device.device_id;
                                }
                            } catch(e){}
                        }
                        var m4 = url.match(/connect-state\/v1\/player\/command/);
                        if(m4 && init && init.headers) {
                            var body = init.body;
                            if(body && typeof body==='string') {
                                try {
                                    var j = JSON.parse(body);
                                    if(j && j.command && j.command.context && j.command.context.uri) {
                                        var m5 = j.command.context.uri.match(/spotify:track:([A-Za-z0-9]+)/);
                                        if(m5) {
                                            window.lastPlayedTrack = m5[1];
                                        }
                                    }
                                } catch(e){}
                            }
                        }
                    }
                    var method = (init && init.method) ? String(init.method).toUpperCase() : 'GET';
                    var gqlOp = null;
                    if(!window.__splOwnCall && url && url.indexOf && url.indexOf('api-partner.spotify.com/pathfinder/v2/query') !== -1 && init && init.body) {
                        try {
                            var qb = typeof init.body==='string' ? JSON.parse(init.body) : init.body;
                            if(qb && qb.operationName && qb.extensions && qb.extensions.persistedQuery && qb.extensions.persistedQuery.sha256Hash) {
                                window.splOpHashes[qb.operationName] = qb.extensions.persistedQuery.sha256Hash;
                                gqlOp = qb.operationName;
                            }
                        } catch(e){}
                    }
                    /* GET form: operationName in the query string (pathfinder v1). */
                    if(!gqlOp && !window.__splOwnCall && url && url.indexOf && url.indexOf('pathfinder') !== -1) {
                        try { var mOp = String(url).match(/[?&]operationName=([^&]+)/); if(mOp) gqlOp = decodeURIComponent(mOp[1]); } catch(e){}
                    }
                    if(url && url.indexOf && (url.indexOf('connect-state') !== -1 || url.indexOf('melody/v1/msg') !== -1 || url.indexOf('/track-playback/') !== -1) && window.mngFetch) {
                        return window.mngFetch(input, init);
                    }
                    if(window.ffDone && url && url.indexOf && url.indexOf('/track-playback/') !== -1 && method === 'PUT' && init && init.body && !window.__splRemoteActive()) {
                        try {
                            var pb = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
                            if(pb && pb.state_ref && pb.state_ref.paused === true && window.playing) {
                                window.actPlayPause(false);
                            } else if(pb && pb.state_ref && pb.state_ref.paused === false && !window.playing) {
                                window.actPlayPause(true);
                            }
                        } catch(e){}
                    }
                    var p = orig.call(window, input, init);
                    if(!window.__splOwnCall && url && url.indexOf && url.indexOf('pathfinder') !== -1) {
                        var opName = gqlOp;
                        p.then(function(resp){
                            try { resp.clone().json().then(function(j){ window.splGqlPublish(opName, j); }).catch(function(){}); } catch(e){}
                        }).catch(function(){});
                    }
                    if(gqlOp === 'queryArtistOverview') {
                        p.then(function(resp){
                            try {
                                resp.clone().json().then(function(j){
                                    try {
                                        var u = j && j.data && j.data.artistUnion;
                                        if(u && u.id) {
                                            window.__splArtistOv = window.__splArtistOv || {};
                                            window.__splArtistOv[u.id] = u;
                                        }
                                    } catch(e){}
                                });
                            } catch(e){}
                        });
                    }
                    if(gqlOp && /album|tracks/i.test(gqlOp)) {
                        p.then(function(resp){
                            try {
                                resp.clone().text().then(function(t){
                                    try {
                                        if(t.indexOf('"playcount"') === -1) return;
                                        window.__splPlays = window.__splPlays || {};
                                        var walk = function(o){
                                            if(!o || typeof o !== 'object') return;
                                            if(typeof o.uri === 'string' && o.uri.indexOf('spotify:track:') === 0 && o.playcount != null) window.__splPlays[o.uri.slice(14)] = o.playcount;
                                            for(var k in o) walk(o[k]);
                                        };
                                        walk(JSON.parse(t));
                                    } catch(e){}
                                });
                            } catch(e){}
                        });
                    }
                    if(gqlOp === 'canvas' && window.__splFullPlayer) {
                        return p.then(function(resp){
                            return resp.clone().json().then(function(j){
                                if(j && j.data && j.data.trackUnion) j.data.trackUnion.canvas = null;
                                return new Response(JSON.stringify(j), {status: resp.status, statusText: resp.statusText, headers: resp.headers});
                            }).catch(function(){ return resp; });
                        });
                    }
                    if(url && url.indexOf && url.indexOf('/metadata/4/track/') !== -1) {
                        p.then(function(resp){
                            try {
                                resp.clone().text().then(function(t){
                                    try {
                                        var j = JSON.parse(t);
                                        if(j && j.canonical_uri) {
                                            var tid = j.canonical_uri.replace('spotify:track:','');
                                            var cg = j.album && j.album.cover_group && j.album.cover_group.image;
                                            window.__splTrackMeta = window.__splTrackMeta || {};
                                            window.__splTrackMeta[tid] = {
                                                uri: j.canonical_uri,
                                                name: j.name || null,
                                                artist: (j.artist && j.artist.length) ? j.artist[0].name : null,
                                                album: (j.album) ? j.album.name : null,
                                                cover: (cg && cg.length) ? 'https://i.scdn.co/image/' + cg[0].file_id : null,
                                                trackNumber: (typeof j.number === 'number' && j.number > 0) ? j.number : null,
                                                discNumber: (typeof j.disc_number === 'number' && j.disc_number > 0) ? j.disc_number : null,
                                                year: (j.album && j.album.date && j.album.date.year) ? j.album.date.year : null
                                            };
                                            var splPlayingId = (typeof window.splPlayingTrackId === 'function')
                                                ? window.splPlayingTrackId()
                                                : (window.splTrackId || null);
                                            if(!splPlayingId || tid === splPlayingId) {
                                                window.__curTrackUri = j.canonical_uri;
                                                window.__curTrackId = tid;
                                                window.__curTrackName = j.name || null;
                                                window.__curTrackArtist = (j.artist && j.artist.length) ? j.artist[0].name : null;
                                                window.__curTrackAlbum = (j.album) ? j.album.name : null;
                                                if(cg && cg.length) window.__curTrackCover = 'https://i.scdn.co/image/' + cg[0].file_id;
                                                window.lastState = null;
                                                if(window.updMedia && typeof track !== 'undefined'){ try{ window.updMedia(); }catch(e){} }
                                            }
                                        }
                                    } catch(e){}
                                });
                            } catch(e){}
                        });
                    }
                    return p;
                };
            })();
        
    """
}
