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

            async function apiGet(url){
                await window.ensureAuthToken();
                var f = window.mngFetch || window.oriFetch || window.fetch;
                var r = await f(url, { headers: { 'Authorization': window.spotAuthToken } });
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            }

            function img(o, i){
                var a = o && o.images;
                return (a && (a[i] || a[0])) ? (a[i] || a[0]).url : '';
            }

            window.splNativeArtist = async function(uri){
                var id = String(uri).split(':').pop();
                var market = ((navigator.language || 'en-US').split('-')[1] || 'US').toUpperCase();
                var errs = [];
                var a = await apiGet('https://api.spotify.com/v1/artists/' + id);
                var t = { tracks: [] }, al = { items: [] };
                try { t = await apiGet('https://api.spotify.com/v1/artists/' + id + '/top-tracks?market=' + market); } catch(e){ errs.push('top tracks ' + e.message); }
                try { al = await apiGet('https://api.spotify.com/v1/artists/' + id + '/albums?include_groups=album,single&limit=30&market=' + market); } catch(e){ errs.push('albums ' + e.message); }
                return {
                    name: a.name,
                    image: img(a, 0),
                    followers: (a.followers && a.followers.total) || 0,
                    tracks: (t.tracks || []).map(function(x){
                        return { uri: x.uri, name: x.name, album: x.album && x.album.name, image: img(x.album, 1), duration: x.duration_ms };
                    }),
                    albums: (al.items || []).map(function(x){
                        return { id: x.id, uri: x.uri, name: x.name, image: img(x, 1), year: (x.release_date || '').slice(0, 4), type: x.album_type };
                    }),
                    errors: errs
                };
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
                    } else {
                        fail('unknown op');
                    }
                } catch(e){ fail(e); }
            };
        })();
    """
}
