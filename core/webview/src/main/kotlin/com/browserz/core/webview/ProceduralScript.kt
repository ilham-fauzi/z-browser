package com.browserz.core.webview

/**
 * Runs procedural hiding rules (compiled by the filter engine into `["css", ["op", "arg"], ...]` programs) in the
 * page, and again after the page changes, since these rules usually target content that loads late (feeds,
 * "Sponsored" cards). Re-injecting on the same page only swaps the rules, it never adds a second observer.
 */
internal object ProceduralScript {

    fun forRules(rulesJson: String): String = "(function(R){try{if(window.__bzProc){window.__bzProc(R);return;}$RUNTIME}catch(e){}})($rulesJson);"

    private const val RUNTIME =
        "var rules=R;" +
            "var txt=function(a){var m=/^\\/(.*)\\/([imsu]*)$/.exec(a);if(m){try{var re=new RegExp(m[1],m[2]);return function(s){return re.test(s);};}catch(x){return function(){return false;};}}" +
            "return function(s){return s.indexOf(a)>=0;};};" +
            "var add=function(out,e){if(e&&out.indexOf(e)<0)out.push(e);};" +
            "var find=function(p,root){var els;try{els=Array.prototype.slice.call(root.querySelectorAll(p[0]));}catch(x){return {els:[],rm:false};}var rm=false;" +
            "for(var i=1;i<p.length&&els.length;i++){var op=p[i][0],a=p[i][1],out=[];" +
            "if(op==='text'){var t=txt(a);els.forEach(function(e){if(t(e.textContent||''))out.push(e);});}" +
            "else if(op==='has'){els.forEach(function(e){try{if(e.querySelector(a))out.push(e);}catch(x){}});}" +
            "else if(op==='hasp'){els.forEach(function(e){if(find(a,e).els.length)out.push(e);});}" +
            "else if(op==='up'){var n=parseInt(a,10);els.forEach(function(e){var u=e;if(n>0){for(var k=0;k<n&&u;k++)u=u.parentElement;}" +
            "else{try{u=e.parentElement&&e.parentElement.closest(a);}catch(x){u=null;}}add(out,u);});}" +
            "else if(op==='down'){els.forEach(function(e){try{e.querySelectorAll(a).forEach(function(c){add(out,c);});}catch(x){}});}" +
            "else if(op==='is'){els.forEach(function(e){try{if(e.matches(a))out.push(e);}catch(x){}});}" +
            "else if(op==='remove'){rm=true;out=els;}" +
            "els=out;}return {els:els,rm:rm};};" +
            "var run=function(p){var r=find(p,document);r.els.forEach(function(e){if(e===document.documentElement||e===document.body||e===document.head)return;" +
            "if(r.rm){e.remove();}else{e.style.setProperty('display','none','important');}});};" +
            "var all=function(){for(var i=0;i<rules.length;i++)run(rules[i]);};" +
            "window.__bzProc=function(r){rules=r;all();};all();" +
            "var q=false;new MutationObserver(function(){if(q)return;q=true;setTimeout(function(){q=false;all();},150);})" +
            ".observe(document.documentElement,{childList:true,subtree:true,characterData:true});"
}
