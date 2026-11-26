package com.project.lol.webview.injections

/*
 * Calls from the native UI layer: search, artist data, play and navigation run
 * inside the page, which already holds the login token, and the JSON answer is
 * sent back through AndBridge.nativeResult.
 */

object NativeApi {
    const val CONTENT = """
        (function () {
            if (window.splNative) return;

            window.splNativeNav = function(path){
                try {
                    history.pushState({}, '', path);
                    window.dispatchEvent(new PopStateEvent('popstate', { state: null }));
                } catch(e){ location.href = path; }
            };

            function sleep(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }

            function src(o){
                var a = o && o.sources;
                return (a && a.length) ? (a[Math.min(1, a.length - 1)].url || a[0].url || '') : '';
            }

            function mapRelease(r){
                if (!r) return null;
                var id = r.id || String(r.uri || '').split(':').pop();
                if (!id) return null;
                return {
                    id: id, uri: r.uri || ('spotify:album:' + id), name: r.name || '',
                    image: src(r.coverArt), year: String((r.date && r.date.year) || ''),
                    type: String(r.type || 'ALBUM').toLowerCase()
                };
            }

            window.splNativeArtist = async function(uri){
                var id = String(uri).split(':').pop();
                window.__splArtistOv = window.__splArtistOv || {};
                var u = window.__splArtistOv[id];
                if (!u) {
                    window.splNativeNav('/artist/' + id);
                    for (var n = 0; n < 50 && !window.__splArtistOv[id]; n++) await sleep(300);
                    u = window.__splArtistOv[id];
                }
                if (!u) throw new Error('the web page did not load the artist');
                var d = u.discography || {};
                var top = ((d.topTracks && d.topTracks.items) || []).map(function(x){
                    var t = x.track || x;
                    var al = t.albumOfTrack || {};
                    return {
                        uri: t.uri, name: t.name, album: al.name || '', image: src(al.coverArt),
                        duration: (t.duration && t.duration.totalMilliseconds) || 0,
                        plays: String(t.playcount || '')
                    };
                }).filter(function(t){ return t.uri; });
                var rel = [];
                var add = function(r){ var m = mapRelease(r); if (m && !rel.some(function(q){ return q.id === m.id; })) rel.push(m); };
                ((d.popularReleasesAlbums && d.popularReleasesAlbums.items) || []).forEach(add);
                ['albums', 'singles', 'compilations'].forEach(function(k){
                    ((d[k] && d[k].items) || []).forEach(function(g){
                        ((g.releases && g.releases.items) || [g]).forEach(add);
                    });
                });
                var vis = u.visuals || {};
                var hdr = (vis.headerImage && vis.headerImage.data) || vis.headerImage || null;
                var errs = [];
                if (!top.length) errs.push('no top tracks (keys: ' + Object.keys(d).join(',') + ')');
                return {
                    name: (u.profile && u.profile.name) || '',
                    image: src(hdr) || src(vis.avatarImage),
                    followers: (u.stats && u.stats.followers) || 0,
                    monthly: (u.stats && u.stats.monthlyListeners) || 0,
                    tracks: top, albums: rel, errors: errs
                };
            };

            function textOf(v){ return String(v || '').replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/&#x27;|&#39;/g, "'").replace(/&quot;/g, '"').trim(); }

            function homeItem(it){
                var c = (it && it.content) || it || {};
                var d = c.data || c;
                var uri = (it && it.uri) || d.uri || c.uri || '';
                var kind = String(d.__typename || c.__typename || '').replace(/ResponseWrapper$/, '');
                var name = d.name || (d.profile && d.profile.name) || '';
                if (!uri || !name) return null;
                var image = src(d.coverArt) || src(d.images && d.images.items && d.images.items[0]) ||
                    src(d.visuals && d.visuals.avatarImage) || src(d.podcastV2 && d.podcastV2.data && d.podcastV2.data.coverArt);
                var sub = '';
                if (d.artists && d.artists.items) sub = d.artists.items.map(function(a){ return a.profile && a.profile.name; }).filter(Boolean).join(', ');
                else if (d.ownerV2 && d.ownerV2.data) sub = d.ownerV2.data.name || '';
                else if (d.publisher) sub = d.publisher.name || '';
                if (!sub && d.description) sub = textOf(d.description);
                return { uri: uri, name: name, image: image, subtitle: sub, kind: kind.toLowerCase() };
            }

            function parseHome(h){
                var sections = (h && h.sectionContainer && h.sectionContainer.sections && h.sectionContainer.sections.items) || [];
                var out = [];
                sections.forEach(function(sec){
                    var sd = sec.data || {};
                    var items = ((sec.sectionItems && sec.sectionItems.items) || []).map(homeItem).filter(Boolean);
                    if (!items.length) return;
                    var t = sd.title || {};
                    out.push({
                        title: textOf(t.transformedLabel || t.text || ''),
                        style: /short/i.test(sd.__typename || '') ? 'grid' : 'row',
                        items: items
                    });
                });
                try{ AndBridge.dbg('i', 'home sections: ' + sections.map(function(sec){ return ((sec.data && sec.data.__typename) || '?') + ':' + (((sec.sectionItems && sec.sectionItems.items) || []).length); }).join(', ')); }catch(_){}
                return { greeting: textOf(h && h.greeting && (h.greeting.transformedLabel || h.greeting.text)), sections: out };
            }

            /* Home comes from the web player's own home query, captured in FetchOverride.
               If none was captured yet (or a refresh is asked), the page is sent home so it fetches one. */
            /* Query ids for any GraphQL operation, read from the web player's own JS bundles
               (they carry entries like "home","query","<sha256>"). Learned once per id,
               remembered in localStorage, so any screen can ask for its data directly. */
            var bundleText = null;
            async function bundles(){
                if (bundleText) return bundleText;
                var urls = performance.getEntriesByType('resource').map(function(e){ return e.name; })
                    .concat(Array.prototype.map.call(document.scripts, function(s){ return s.src; }))
                    .filter(function(u){ return u && /spotifycdn\.com\/cdn\/build\/web-player\/.*\.js(\?|$)/.test(u); });
                urls = urls.filter(function(u, i){ return urls.indexOf(u) === i; });
                var texts = await Promise.all(urls.map(function(u){ return window.oriFetch(u).then(function(r){ return r.text(); }).catch(function(){ return ''; }); }));
                bundleText = texts.join('\n');
                try { AndBridge.dbg('s', 'gql bundles read: ' + urls.length + ' files, ' + bundleText.length + ' chars'); } catch(e){}
                return bundleText;
            }
            /* All query ids the bundles hold for an operation (a bundle may carry more than one version). */
            async function hashesFor(op){
                var out = [];
                var known = window.getGqlHash ? window.getGqlHash(op, null) : (window.splOpHashes || {})[op];
                if (known) out.push(known);
                var txt = await bundles();
                var re = new RegExp('"' + op + '"\\s*,\\s*"query"\\s*,\\s*"([0-9a-f]{64})"', 'g'), m;
                while ((m = re.exec(txt))) if (out.indexOf(m[1]) === -1) out.push(m[1]);
                return out;
            }
            function post(op, hash, variables){
                return window.oriFetch('https://api-partner.spotify.com/pathfinder/v2/query', {
                    method: 'POST',
                    headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken || '', 'Content-Type': 'application/json;charset=UTF-8', 'App-Platform': 'WebPlayer' },
                    body: JSON.stringify({ variables: variables, operationName: op, extensions: { persistedQuery: { version: 1, sha256Hash: hash } } })
                }).then(function(r){ return r.text().then(function(t){ var j = null; try { j = JSON.parse(t); } catch(e){} return { status: r.status, text: t, json: j }; }); });
            }
            /* Runs a GraphQL operation itself and publishes the answer on the bus. Each known
               query id is tried; when the server names a missing or unexpected variable, the
               variables are fixed and the call is repeated. */
            window.splGql = async function(op, variables){
                await window.ensureAuthToken();
                if (!window.spotAuthToken) throw new Error('not signed in');
                var hashes = await hashesFor(op);
                if (!hashes.length) throw new Error('no query id for ' + op);
                var last = '';
                for (var h = 0; h < hashes.length; h++) {
                    var vars = Object.assign({}, variables || {});
                    for (var attempt = 0; attempt < 4; attempt++) {
                        var r = await post(op, hashes[h], vars);
                        if (r.json && r.json.data) {
                            window.splOpHashes[op] = hashes[h];
                            try { localStorage.setItem('sp_hash_' + op, hashes[h]); } catch(e){}
                            window.splGqlPublish(op, r.json);
                            return r.json.data;
                        }
                        last = 'http ' + r.status + ' ' + String(r.text || '').slice(0, 300);
                        try { AndBridge.dbg('s', 'gql ' + op + ' #' + h + ' vars=' + JSON.stringify(vars) + ' -> ' + last); } catch(e){}
                        var msg = (r.json && r.json.errors && r.json.errors[0] && r.json.errors[0].message) || r.text || '';
                        var miss = /missing variable [`'"]?\$?([A-Za-z_]+)[`'"]?(?:.*?type [`'"]?([A-Za-z_]+))?/i.exec(msg) ||
                                   /[Vv]ariable [`'"]?\$?([A-Za-z_]+)[`'"]?[^.]*(?:not provided|required|was not)/.exec(msg);
                        var extra = /[Vv]ariable [`'"]?\$?([A-Za-z_]+)[`'"]?[^.]*(not (defined|used|declared)|unknown|unexpected)/.exec(msg);
                        var bad = /invalid variable [`'"]?\$?([A-Za-z_]+)[`'"]?.*?type [`'"]?([A-Za-z_]+)/i.exec(msg);
                        if (miss && !(miss[1] in vars)) { vars[miss[1]] = await defaultFor(miss[1], miss[2] || ''); continue; }
                        if (bad && !vars['__tried_' + bad[1]]) { vars['__tried_' + bad[1]] = 1; vars[bad[1]] = await defaultFor(bad[1], bad[2], true); delete vars['__tried_' + bad[1]]; continue; }
                        if (extra && (extra[1] in vars)) { delete vars[extra[1]]; continue; }
                        break;
                    }
                }
                try { localStorage.removeItem('sp_hash_' + op); } catch(e){}
                delete window.splOpHashes[op];
                throw new Error(op + ' failed: ' + last.slice(0, 160));
            };
            /* A value for a variable the server says is missing. Enum-like values are read from
               the web player's bundles, where the page passes them (e.g. name:"SOME_VALUE"). */
            async function defaultFor(name, type, wasInvalid){
                if (/limit/i.test(name)) return 10;
                if (/offset/i.test(name)) return 0;
                if (/locale/i.test(name)) return navigator.language || 'en';
                if (/time.?zone/i.test(name)) { try { return Intl.DateTimeFormat().resolvedOptions().timeZone; } catch(e){ return 'UTC'; } }
                if (/^Boolean/.test(type) || /^(include|is|enable)/i.test(name)) return false;
                if (/^(Int|Float)/.test(type)) return 0;
                if (type && !/^(String|ID)/.test(type)) {
                    /* Enum: its values are UPPER_SNAKE strings in the bundles that share a word
                       with the type name (HomeEndUserIntegration -> INTEGRATION_*); the web
                       player's own value is preferred. */
                    var txt = await bundles();
                    var words = type.replace(/([a-z])([A-Z])/g, '$1 $2').toUpperCase().split(' ').filter(function(w){ return w.length > 3; });
                    var re = /"([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+)"/g, m, seen = {};
                    while ((m = re.exec(txt))) { var v = m[1]; if (!seen[v] && words.some(function(w){ return v.indexOf(w + '_') === 0; })) seen[v] = 1; }
                    var vals = Object.keys(seen);
                    var pick = vals.filter(function(v){ return /WEB_PLAYER$/.test(v); })[0] || vals.filter(function(v){ return /WEB/.test(v); })[0] || vals[0];
                    try { AndBridge.dbg('s', 'gql var ' + name + ' (' + type + ') -> ' + (pick || 'none') + ' of ' + vals.length + ' values'); } catch(e){}
                    if (pick) return pick;
                }
                return '';
            }

            window.splNativeHome = async function(refresh){
                if (window.__splHome && !refresh) return parseHome(window.__splHome);
                var tz = 'UTC'; try { tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch(e){}
                var spt = ''; try { spt = (document.cookie.match(/(?:^|; )sp_t=([^;]+)/) || [])[1] || ''; } catch(e){}
                try {
                    await window.splGql('home', { timeZone: tz, sp_t: spt, facet: '', sectionItemsLimit: 10, homeEndUserIntegration: 'INTEGRATION_WEB_PLAYER', includeHomeChapterVideoCards: false, includeEpisodeContentRatingsV2: false });
                } catch(e) {
                    try { AndBridge.dbg('s', 'home request failed: ' + e.message); } catch(_){}
                    if (!window.__splHome) throw e;
                }
                if (!window.__splHome) throw new Error('home answer had no sections');
                return parseHome(window.__splHome);
            };

            /* Opens the album/playlist in the (hidden) web page and runs the same collection
               download the page's own download button runs, once the tracklist is on screen. */
            window.splNativeDownloadCollection = async function(path){
                window.splNativeNav(path);
                for (var n = 0; n < 50; n++) {
                    await sleep(300);
                    if (location.pathname.indexOf(path) !== -1 && typeof window.splColDownloadNow === 'function' &&
                        document.querySelector('[data-testid="track-list"],[role=grid]') &&
                        document.querySelector('a[data-testid="internal-track-link"]')) {
                        await sleep(600);
                        window.splColDownloadNow();
                        return { ok: true };
                    }
                }
                throw new Error('the tracklist did not load');
            };

            var B62 = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
            function gidOf(id){
                var n = 0n;
                for (var k = 0; k < id.length; k++) n = n * 62n + BigInt(B62.indexOf(id[k]));
                return n.toString(16).padStart(32, '0');
            }

            async function trackInfo(id){
                var m = window.__splTrackMeta && window.__splTrackMeta[id];
                if (m && m.name) return { name: m.name, artist: m.artist || '', image: m.cover || '' };
                window.__splInfo = window.__splInfo || {};
                if (window.__splInfo[id]) return window.__splInfo[id];
                try {
                    var f = window.mngFetch || window.oriFetch || window.fetch;
                    var r = await f('https://spclient.wg.spotify.com/metadata/4/track/' + gidOf(id) + '?market=from_token', {
                        headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken, 'Accept': 'application/json' }
                    });
                    if (!r.ok) { try{ AndBridge.dbg('w', 'queue meta http ' + r.status + ' ' + id); }catch(_){} return { name: '', artist: '', image: '' }; }
                    var j = await r.json();
                    var cg = j.album && j.album.cover_group && j.album.cover_group.image;
                    var res = {
                        name: j.name || '',
                        artist: (j.artist || []).map(function(a){ return a.name; }).join(', '),
                        image: (cg && cg.length) ? 'https://i.scdn.co/image/' + cg[0].file_id : ''
                    };
                    if (res.name) window.__splInfo[id] = res;
                    return res;
                } catch(e){ try{ AndBridge.dbg('w', 'queue meta failed ' + id + ' ' + (e && e.message || e)); }catch(_){} return { name: '', artist: '', image: '' }; }
            }

            window.splNativeQueue = async function(){
                await window.ensureAuthToken();
                var sm = window.__splSM;
                if (!sm || !sm.tracks) throw new Error('no queue data yet, play or skip a track first');
                var cur = 'spotify:track:' + (window.splTrackId || '');
                var uris = [];
                sm.tracks.forEach(function(t){
                    var u = t && t.metadata && t.metadata.uri;
                    if (u && u.indexOf('spotify:track:') === 0) uris.push(u);
                });
                var ctx = window.__splCtxUri || null;
                (function scan(o, d){
                    if (ctx || !o || d > 6) return;
                    if (typeof o === 'string') { if (/^spotify:(playlist|album):[A-Za-z0-9]{22}$/.test(o)) ctx = o; return; }
                    if (typeof o === 'object') for (var k in o) scan(o[k], d + 1);
                })(sm, 0);
                var qc = window.__splQCtx;
                var qcHit = false;
                if (ctx && qc && qc.ctx === ctx && Date.now() - qc.at < 600000 && qc.uris.indexOf(cur) !== -1) { uris = qc.uris; qcHit = true; }
                if (ctx && !qcHit) {
                    try {
                        var f = window.mngFetch || window.oriFetch || window.fetch;
                        var hdr = { headers: { 'Authorization': window.spotAuthToken, 'Client-Token': window.spotCliToken } };
                        var full = [];
                        var url = 'https://spclient.wg.spotify.com/context-resolve/v1/' + ctx;
                        var seen = {};
                        var ctxMeta = 0;
                        for (var pg = 0; pg < 40 && url && !seen[url]; pg++) {
                            seen[url] = 1;
                            var r = await f(url, hdr);
                            try{ AndBridge.dbg('i', 'queue page ' + pg + ' status ' + r.status + ' ' + url.slice(0, 120)); }catch(_){}
                            if (!r.ok) break;
                            var cj = await r.json();
                            var nx = null;
                            (function walk(o, d){
                                if (!o || d > 8) return;
                                if (Array.isArray(o)) { o.forEach(function(x){ walk(x, d + 1); }); return; }
                                if (typeof o === 'object') {
                                    if (typeof o.uri === 'string' && o.uri.indexOf('spotify:track:') === 0) {
                                        full.push(o.uri);
                                        var md = o.metadata;
                                        if (md && md.title) {
                                            var im = md.image_url || md.image_large_url || '';
                                            if (im.indexOf('spotify:image:') === 0) im = 'https://i.scdn.co/image/' + im.slice(14);
                                            window.__splInfo = window.__splInfo || {};
                                            window.__splInfo[o.uri.split(':').pop()] = { name: md.title, artist: md.artist_name || '', image: im };
                                            ctxMeta++;
                                        }
                                        return;
                                    }
                                    if (typeof o.next_page_url === 'string' && o.next_page_url) nx = o.next_page_url;
                                    for (var k in o) walk(o[k], d + 1);
                                }
                            })(cj.pages || cj, 0);
                            if (pg === 0) { try{ AndBridge.dbg('i', 'queue first page keys ' + Object.keys(cj).join(',') + ' next=' + nx); }catch(_){} }
                            url = nx ? (nx.indexOf('hm://') === 0 ? 'https://spclient.wg.spotify.com/' + nx.slice(5) : nx) : null;
                        }
                        try{ AndBridge.dbg('i', 'queue ctx tracks ' + full.length + ' with metadata ' + ctxMeta); }catch(_){}
                        if (full.length > uris.length) uris = full;
                    } catch(e){ try{ AndBridge.dbg('e', 'queue ctx failed ' + (e && e.message || e)); }catch(_){} }
                }
                if (ctx && !qcHit && ctx.indexOf('spotify:playlist:') === 0) {
                    try {
                        var pl = [];
                        for (var off = 0; off < 600; off += 100) {
                            var pr = await (window.mngFetch || window.oriFetch || window.fetch)('https://api-partner.spotify.com/pathfinder/v2/query', {
                                method: 'POST',
                                headers: {
                                    'Authorization': window.spotAuthToken,
                                    'Client-Token': window.spotCliToken,
                                    'Content-Type': 'application/json;charset=UTF-8',
                                    'app-platform': 'WebPlayer',
                                    'spotify-app-version': '1.2.40.0'
                                },
                                body: JSON.stringify({
                                    variables: { uri: ctx, offset: off, limit: 100, enableWatchFeedEntrypoint: false },
                                    operationName: 'fetchPlaylist',
                                    extensions: { persistedQuery: { version: 1, sha256Hash: window.getGqlHash('fetchPlaylist', '346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763') } }
                                })
                            });
                            if (!pr.ok) { try{ AndBridge.dbg('i', 'queue gql playlist status ' + pr.status); }catch(_){} break; }
                            var pj = await pr.json();
                            var po = pj.data && (pj.data.playlistV2 || pj.data.playlist);
                            var its = (po && po.content && po.content.items) || [];
                            its.forEach(function(el){
                                var d = (el.itemV2 && el.itemV2.data) || {};
                                var u = d.uri || '';
                                if (u.indexOf('spotify:track:') === 0) pl.push(u);
                            });
                            if (its.length < 100) break;
                        }
                        try{ AndBridge.dbg('i', 'queue gql playlist tracks ' + pl.length); }catch(_){}
                        if (pl.indexOf(cur) !== -1 && pl.length > uris.length) uris = pl;
                    } catch(e){ try{ AndBridge.dbg('e', 'queue gql playlist failed ' + (e && e.message || e)); }catch(_){} }
                }
                if (ctx && !qcHit && uris.indexOf(cur) !== -1 && uris.length > 8) window.__splQCtx = { ctx: ctx, uris: uris, at: Date.now() };
                function after(list){
                    var at = [];
                    list.forEach(function(u, i){ if (u === cur) at.push(i); });
                    if (!at.length) return list.slice();
                    var k = at[0];
                    if (at.length > 1) {
                        var nxs = (window.__splNext && window.__splNext.uris) || [];
                        var best = -1;
                        at.forEach(function(i){
                            var m = 0;
                            while (m < 3 && m < nxs.length && list[i + 1 + m] === nxs[m]) m++;
                            if (m > best) { best = m; k = i; }
                        });
                    }
                    return list.slice(k + 1);
                }
                var rows = [];
                document.querySelectorAll('div[data-testid="tracklist-row"] a[href*="/track/"]').forEach(function(a){
                    var m = (a.getAttribute('href') || '').match(/\/track\/([A-Za-z0-9]{22})/);
                    if (m) rows.push('spotify:track:' + m[1]);
                });
                var nx = window.__splNext;
                var nxl = [];
                if (nx && nx.uris && (!ctx || !nx.ctx || nx.ctx === ctx)) {
                    for (var ni = 0; ni < nx.uris.length; ni++) {
                        if (nx.uris[ni] === cur) break;
                        nxl.push(nx.uris[ni]);
                    }
                }
                var cands = [after(uris), rows.indexOf(cur) !== -1 ? after(rows) : [], nxl];
                try{ AndBridge.dbg('i', 'queue sources ctx/sm ' + cands[0].length + ' dom ' + cands[1].length + ' state ' + cands[2].length); }catch(_){}
                var next = (cands[1].length > cands[0].length ? cands[1] : cands[0]);
                if (!next.length) next = cands[2];
                next = next.slice(0, 100);
                var out = new Array(next.length);
                var idx = 0;
                async function worker(){
                    while (idx < next.length) {
                        var k = idx++;
                        var info = await trackInfo(next[k].split(':').pop());
                        out[k] = { uri: next[k], name: info.name, artist: info.artist, image: info.image };
                    }
                }
                await Promise.all([worker(), worker(), worker(), worker(), worker()]);
                var good = out.filter(function(t){ return t && t.name; });
                try{ AndBridge.dbg('i', 'queue resolved ' + good.length + ' of ' + next.length); }catch(_){}
                return { items: good };
            };

            window.splNative = function(reqId, op, argStr){
                var arg = null;
                try { arg = JSON.parse(argStr); } catch(e){ arg = argStr; }
                var done = function(v){ try { AndBridge.nativeResult(reqId, JSON.stringify(v)); } catch(e){} };
                var fail = function(e){ done({ error: String((e && e.message) || e) }); };
                try {
                    if (op === 'search') {
                        window.ensureAuthToken().then(function(){ return window.searchMediaItems(arg); }).then(done, fail);
                    } else if (op === 'artist') {
                        window.splNativeArtist(arg).then(done, fail);
                    } else if (op === 'canvas') {
                        var same = window.__splCanvasFor && window.__splCanvasFor === window.splTrackId;
                        done({ url: same ? (window.__splCanvasUrl || '') : '' });
                    } else if (op === 'video') {
                        var vi = window.__splVfsInfo ? window.__splVfsInfo() : null;
                        done(vi ? { ok: true, cur: vi.cur, dur: vi.dur, paused: vi.paused, fs: vi.fs } : { ok: false });
                    } else if (op === 'track') {
                        done({ id: (typeof window.splPlayingTrackId === 'function' && window.splPlayingTrackId()) || window.splTrackId || window.__curTrackId || '' });
                    } else if (op === 'downloadCollection') {
                        window.splNativeDownloadCollection(arg).then(done, fail);
                    } else if (op === 'home') {
                        window.splNativeHome(!!arg).then(done, fail);
                    } else if (op === 'queue') {
                        window.splNativeQueue().then(done, fail);
                    } else {
                        fail('unknown op');
                    }
                } catch(e){ fail(e); }
            };
            try { AndBridge.nativeReady(); } catch(e){}
        })();
    """
}
