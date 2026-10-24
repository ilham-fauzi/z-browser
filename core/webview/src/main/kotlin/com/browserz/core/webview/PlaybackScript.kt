package com.browserz.core.webview

import org.json.JSONObject

/**
 * Page-side half of background play and picture-in-picture.
 *
 * [HOOK] runs at document start in every frame. It reports audible media that starts, pauses or ends through
 * the `__bzPlay` bridge (muted previews are ignored), remembers the element and the page's own Media Session
 * handlers (next / previous track), and lets the top frame pass a control command down to the frame that plays.
 */
internal object PlaybackScript {

    const val BRIDGE = "__bzPlay"

    const val HOOK = "(function(){try{if(!window.__bzPlay||window.__bzPlayHooked)return;window.__bzPlayHooked=true;" +
        "var H={};try{var ms=navigator.mediaSession;if(ms&&ms.setActionHandler){var o=ms.setActionHandler.bind(ms);" +
        "ms.setActionHandler=function(a,f){H[a]=f;return o(a,f);};}}catch(e){}" +
        "window.__bzMsH=H;" +
        "var meta=function(){try{var m=navigator.mediaSession&&navigator.mediaSession.metadata;return m?{t:m.title||'',a:m.artist||''}:{t:'',a:''};}catch(e){return {t:'',a:''};}};" +
        "var send=function(s,v){var m=meta();window.__bzPlay.postMessage(JSON.stringify({s:s,v:v.tagName==='VIDEO'&&v.videoWidth>0?1:0," +
        "w:v.videoWidth||0,h:v.videoHeight||0,t:(m.t||document.title||'').slice(0,200),a:(m.a||'').slice(0,200)," +
        "n:H.nexttrack?1:0,p:H.previoustrack?1:0,top:window.top===window?1:0}));};" +
        "var on=function(e){var v=e.target;if(!v||(v.tagName!=='VIDEO'&&v.tagName!=='AUDIO'))return;" +
        "if(e.type==='playing'||e.type==='play'||e.type==='volumechange'){if(v.paused)return;if(v.muted||v.volume===0){if(window.__bzLast===v)send('pause',v);return;}" +
        "window.__bzLast=v;send('play',v);}else if(window.__bzLast===v){send(e.type==='ended'?'ended':'pause',v);}};" +
        "['play','playing','pause','ended','volumechange'].forEach(function(n){document.addEventListener(n,on,true);});" +
        // Commands from the top frame (see control) reach the frame that owns the player.
        "window.addEventListener('message',function(e){var d=e.data;if(!d||typeof d!=='object'||typeof d.__bz!=='string')return;" +
        "if(e.source!==window.parent&&e.source!==window)return;window.__bzCmd(d.__bz);});" +
        "window.__bzCmd=function(a){var v=window.__bzLast;" +
        "if(a==='next'&&H.nexttrack){H.nexttrack({action:'nexttrack'});return;}" +
        "if(a==='previous'&&H.previoustrack){H.previoustrack({action:'previoustrack'});return;}" +
        "if(v){if(a==='play')v.play();else if(a==='pause')v.pause();}" +
        "for(var i=0;i<window.frames.length;i++){try{window.frames[i].postMessage({__bz:a},'*');}catch(e){}}};" +
        "}catch(e){}})();"

    /** Plays, pauses or skips the media of the page (any frame). [action] is play, pause, next or previous. */
    fun control(action: String): String = "(function(){try{if(window.__bzCmd)window.__bzCmd(${JSONObject.quote(action)});}catch(e){}})();"

    /**
     * Installed while background play is on: the page keeps believing it is visible when the app is not on screen,
     * since sites (YouTube among them) pause playback when the page is hidden. Only takes effect on pages loaded
     * after the switch is turned on.
     */
    const val STAY_VISIBLE = "(function(){try{if(window.__bzVisible)return;window.__bzVisible=true;" +
        "Object.defineProperty(document,'hidden',{configurable:true,get:function(){return false;}});" +
        "Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return 'visible';}});" +
        "Object.defineProperty(document,'webkitHidden',{configurable:true,get:function(){return false;}});" +
        "Object.defineProperty(document,'webkitVisibilityState',{configurable:true,get:function(){return 'visible';}});" +
        "var stop=function(e){e.stopImmediatePropagation();};" +
        "['visibilitychange','webkitvisibilitychange'].forEach(function(n){window.addEventListener(n,stop,true);document.addEventListener(n,stop,true);});" +
        "}catch(e){}})();"

    /**
     * Picture-in-picture shows the whole page in a small window, so the playing video (or the frame that holds it)
     * is laid over the page at window size; [on] = false puts the page back as it was.
     */
    fun pictureInPicture(on: Boolean): String = "(function(on){try{var A='data-bz-pip',s=document.getElementById('__bz_pip');" +
        "var old=document.querySelectorAll('['+A+']');for(var i=0;i<old.length;i++)old[i].removeAttribute(A);" +
        "if(!on){if(s)s.remove();return;}" +
        "var v=window.__bzLast;if(!v||v.tagName!=='VIDEO'||!v.isConnected){v=null;var best=0;" +
        "document.querySelectorAll('video,iframe').forEach(function(e){var r=e.getBoundingClientRect(),a=r.width*r.height;" +
        "if(e.tagName==='VIDEO'&&!e.paused)a*=4;if(a>best){best=a;v=e;}});}" +
        "if(!v)return;v.setAttribute(A,'1');" +
        "if(!s){s=document.createElement('style');s.id='__bz_pip';(document.head||document.documentElement).appendChild(s);}" +
        "s.textContent='['+A+']{position:fixed!important;left:0!important;top:0!important;right:auto!important;bottom:auto!important;" +
        "width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;margin:0!important;" +
        "z-index:2147483647!important;background:#000!important;object-fit:contain!important;transform:none!important;border:0!important}" +
        // Ancestors with a transform, filter or containment would trap the fixed element inside them.
        ":has(['+A+']){transform:none!important;filter:none!important;contain:none!important;will-change:auto!important}" +
        // Everything else (sticky headers, banners) is hidden; the video's ancestors stay but show nothing else.
        "body *:not(['+A+']):not(:has(['+A+'])):not(['+A+'] *){visibility:hidden!important}" +
        "html,body{overflow:hidden!important;background:#000!important}';" +
        "}catch(e){}})(" + on + ");"
}
