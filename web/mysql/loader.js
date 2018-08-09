/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
var RH = {
    "menu": {
        "getRadioButton": function (name, id) {
            var i = document.createElement('input');
            i.setAttribute('type', 'radio');
            i.setAttribute('name', name);
            i.setAttribute('id', id);
            i.setAttribute('style', 'display:none;');
            return i;
        },
        "getLabelForID": function (forID, title, key, url, target) {
            var l = document.createElement('label');
            l.setAttribute('for', forID);
            l.setAttribute('id', 'label' + forID);

            l.setAttribute('data-key', key);
            l.setAttribute('data-url', url);
            l.setAttribute('data-target', target);
            l.setAttribute('onclick', 'RH.menu.onClick(this)');

            l.innerHTML = '' + title;
            return l;
        },
        "loadMenu": function (className, checkedIndex , activeIndexStyle ) {
            if (checkedIndex == null) {
                checkedIndex = 0;
            }
            var eles = RH.getElementsByClassName(className);
            for (var i = 0; i < eles.length; i++) {
                var title = eles[i].getAttribute("data-title");
                var key = eles[i].getAttribute("data-key");
                var url = eles[i].getAttribute("data-url");
                var target = eles[i].getAttribute("data-target");
                eles[i].appendChild(this.getRadioButton(className, 'id' + key));
                var label = this.getLabelForID('id' + key, title, key, url, target);
                eles[i].appendChild(label);
                if (i == checkedIndex) {
                    label.click();
                }
            }
            
            if(activeIndexStyle != null){
                document.styleSheets[document.styleSheets.length-1].insertRule('.'+className+' input[type=radio]:checked + label{'+activeIndexStyle+'}',0);
            }
        },
        "onClick": function (ele,fn, isNotPushHistory) {
            var key = ele.getAttribute("data-key");
            var dataClass = ele.getAttribute("data-class");
            var url = ele.getAttribute("data-url");
            var target = ele.getAttribute("data-target");
            var parameter = null;
            if(key != null){
                parameter = "key="+key;
            }
            if(dataClass != null){
                var extras = RH.validation.getUrlByClass(null,dataClass,'id',RH.validation.errorCallBack,null);
                if(extras != null){
                    if(parameter == null){
                        parameter = extras;
                    }else{
                        parameter += "&"+extras;
                    }
                }else{
                    return false;
                }
            }
            console.log("Key:" + key + "; URL:" + url + "; Target:" + target + "; Parameter: " + parameter);
            if(fn == null)
                RH.server.get(url, parameter, target, RH.load, isNotPushHistory);
            else
                RH.server.get(url, parameter, target, fn, isNotPushHistory);
        }
    },
    "server": {
        "get": function (url, parameter, target, callback, isNotPushHistory) {
            var r;
            if (window.XMLHttpRequest) {
                // code for IE7+, Firefox, Chrome, Opera, Safari
                r = new XMLHttpRequest();
            } else {
                // code for IE6, IE5
                r = new ActiveXObject("Microsoft.XMLHTTP");
            }
            r.onreadystatechange = function () {
                if (r.readyState == 4) {
                    RH.g('loading').style.display = 'none';
                    callback(target, r.responseText.trim());
                    if(r.status==200 && !isNotPushHistory){
                        var state = {
                            url : url,
                            parameter : parameter,
                            target : target
                        }
                        history.pushState(state,null,"#"+url);
                    }
                }else{
                    RH.g('loading').style.display = 'block';
                }
            };
            console.log(url);
            r.open("POST", url, true);
            r.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
            console.log(parameter);
            r.send(parameter);
        }
    },
    "load": function (target, response) {
        document.getElementById(target).innerHTML = response;
        var scr = document.getElementById(target).getElementsByTagName('script');
        for (var i = 0; i < scr.length; i++)
            eval(scr[i].innerHTML);
    },
    "getElementsByClassName": function (className, root) {
        var r = root;
        if (root == null) {
            r = document;
        }
        if (r.getElementsByClassName == null) {
            return r.querySelectorAll('.' + className);
        } else {
            return r.getElementsByClassName(className);
        }
    },
    "gC": function (c, ele) {
        return RH.getElementsByClassName(c, ele);
    },
    "g": function (id) {
        return document.getElementById(id);
    },
    "enc": function (v) {
        return encodeURIComponent(v);
    },
    "validation": {
        ipRegx: /^([1-2][0-5][0-5]|[1-2][0-4]\d|[1]\d\d|[1-9]\d|[1-9])(\.([1-2][0-5][0-5]|[1-2][0-4]\d|[1]\d\d|[1-9]\d|\d)){2}\.([1-2][0-5][0-4]|[1-2][0-4]\d|[1]\d\d|[1-9]\d|[2-9])$/,
        contactNumberRegx: /^\+?\d+$/,
        floatRegx: /^\d*\.?\d+$/,
        intRegx: /^\d+$/,
        macRegx: /^([0-9a-fA-F][0-9a-fA-F]-){5}([0-9a-fA-F][0-9a-fA-F])$/,
        validatePort: function (v) {
            return (v >= 0 && v <= 65535);
        },
        validateIP: function (v) {
            return this.ipRegx.test(v);
        },
        validateContactNumber: function (v) {
            return this.contactNumberRegx.test(v);
        },
        validateCountryCode: function (v) {
            return v.length <= 5 && this.contactNumberRegx.test(v);
        },
        validateFloat: function (v) {
            return this.floatRegx.test(v);
        },
        validatePassword: function (v) {
            return v.length >= 4;
        },
        validateInt: function (v) {
            return this.intRegx.test(v);
        },
        validateMac: function (v) {
            return this.macRegx.test(v);
        },
        v: {
            'none': function (v) {
                return true
            },
            'port': function (v) {
                return RH.validatePort(v)
            },
            'ip': function (v) {
                return RH.validateIP(v)
            },
            'phone': function (v) {
                return RH.validateContactNumber(v)
            },
            'cCode': function (v) {
                return RH.validateCountryCode(v)
            },
            'float': function (v) {
                return RH.validateFloat(v)
            },
            'password': function (v) {
                return RH.validatePassword(v)
            },
            'int': function (v) {
                return RH.validateInt(v)
            },
            'mac': function (v) {
                return RH.validateMac(v)
            },
            'nonZero': function (v) {
                return eval(v) > 0
            }
        },
        errMessageFormat: {
            'none': "OK",
            'port': "{0} value should be between 0-65535",
            'ip': "Invalid {0}",
            'phone': "{0} should be number and may contain '+' at start",
            'cCode': "{0} length should be less than 6, be a number and may contain '+' at start",
            'float': "{0} should be a decimal number",
            'password': "{0} length should be greater than 3",
            'int': "{0} should be a Integer",
            'mac': "{0} should be like XX-XX-XX-XX-XX-XX and valid",
            'nonZero': "{0} should be non-zero"
        },
        getUrlByClass: function (ele, className, url_on, callBack, except) {
            var e = RH.gC(className, ele);
            var url = '';
            var val;
            if (e.length > 0)
            {
                if(e[0].type == "select-multiple"){
                    if(e[0].selectedOptions.length == 0){
                        if(e[0].getAttribute('data-nullable')==null){
                            try {callBack({ele: e[0], err: 0})} catch (e1) {} return null;
                        }
                    }else{
                        t = e[0].selectedOptions;
                        url = e[0].getAttribute(url_on)+"="+RH.enc(t[0].value);
                        for(j=1; j<t.length;j++){
                            url+='&'+e[0].getAttribute(url_on)+"="+RH.enc(t[j].value);
                        }
                    }
                }else{
                    val = (e[0].type == "radio") ? (document.querySelector("input[name=" + e[0].name + "]:checked").value) : (e[0].value || e[0].innerHTML);
                    if (val == null || val.length == 0) {
                        if(e[0].getAttribute('data-nullable')==null){
                            try {
                                callBack({ele: e[0], err: 0})
                            } catch (e1) {
                            }
                            return null;
                        }
                    }
                    else if (!this.v[e[0].getAttribute('data-type')](val)) {
                        try {
                            callBack({ele: e[0], err: 1})
                        } catch (e1) {
                        }
                        return null;
                    }else{
                        url = ((e[0].type == "radio")? e[0].name : e[0].getAttribute(url_on))+ "=" + RH.enc(val);
                    }
                }      
            }
            for (i = 1; i < e.length; i++)
            {
                if(e[i].type == "select-multiple"){
                    if(e[i].selectedOptions.length == 0){
                        if(e[i].getAttribute('data-nullable')==null){
                            try {callBack({ele: e[i], err: 0})} catch (e1) {} return null;
                        }
                    }
                    t = e[i].selectedOptions;
                    for(j=0; j<t.length;j++){
                        url+='&'+e[i].getAttribute(url_on)+"="+RH.enc(t[j].value);
                    }
                }else{
                    val = (e[i].type == "radio") ? (document.querySelector("input[name=" + e[i].name + "]:checked").value) : (e[i].value || e[i].innerHTML);
                    if (val == null || val.length == 0) {
                        if(e[i].getAttribute('data-nullable')==null){
                            try {
                                callBack({ele: e[i], err: 0})
                            } catch (e1) {
                            }
                            return null;
                        }
                    }
                    else if (!this.v[e[i].getAttribute('data-type')](val)) {
                        try {
                            callBack({ele: e[i], err: 1})
                        } catch (e1) {
                        }
                        return null;
                    }else{
                        url += '&' + ((e[i].type == "radio")? e[i].name : e[i].getAttribute(url_on)) + "=" + RH.enc(val);
                    }
                }
            }
            return url;
        },
        /**
         * obj = {
         *      ele :   element obj, which fails in validation,
         *      err :   errType
         *              //  0 if Empty
         *              //  1 if fails in validation
         * }
         * 
         */
        errorCallBack: function (obj) {
            var mess;
            if (obj.err == 0) {
                mess = "'" + obj.ele.title + "' can not be left Empty";
            } else if (obj.err == 1) {
                mess = RH.validation.errMessageFormat[obj.ele.classList[0]]().replace("{0}", obj.ele.title);
                //mess = "Invalid '"+obj.ele.title+"'";
            }
            RH.g('dialogMessage').innerHTML = mess;
            obj.ele.focus();
        }
    },
    "navigation":{
        "next": function(ele){
            var s = RH.g('si');
            var e = RH.g('ei');
            var ei = eval(e.innerHTML);
            var si = eval(s.innerHTML);
            s.innerHTML = (ei+1)+'';
            e.innerHTML = (ei+(ei-si+1))+'';
            RH.g('leftNav').removeAttribute('disabled');
            RH.menu.onClick(ele);
        },
        "prev": function(ele){
            var s = RH.g('si');
            var e = RH.g('ei');
            var ei = eval(e.innerHTML);
            var si = eval(s.innerHTML);
            var d = ei-si+1;
            if(si-d < 0){
                s.innerHTML = '1';
                e.innerHTML = d+'';
                RH.g('leftNav').setAttribute('disabled','');
            }else{
                RH.g('rightNav').removeAttribute('disabled');
                s.innerHTML = (si-d)+'';
                e.innerHTML = (ei-d)+'';
            }
            RH.menu.onClick(ele);
        },
        "disableNext": function(){
            RH.g('rightNav').setAttribute('disabled','');
        },
        "loadNavigation": function(id){
            var ele = RH.g(id);
            var url = ele.getAttribute('data-url');
            var target = ele.getAttribute('data-target');
            var c = ele.getAttribute('data-class');
            var diff = ele.getAttribute('data-navDiff');
            if(diff == null){
                diff = '50';
            }
            var l = document.createElement('input');
            l.setAttribute('type','button');
            l.setAttribute('id','leftNav');
            l.setAttribute('class','v_nav');
            l.setAttribute('data-url',url);
            l.setAttribute('data-target', target);
            l.setAttribute('data-class',c);
            l.setAttribute('onClick', 'RH.navigation.prev(this)');
            l.value = '<';
            var r = document.createElement('input');
            r.setAttribute('type','button');
            r.setAttribute('id','rightNav');
            r.setAttribute('class','v_nav');
            r.setAttribute('data-url',url);
            r.setAttribute('data-target', target);
            r.setAttribute('data-class',c);
            r.setAttribute('onClick', 'RH.navigation.next(this)');
            r.value = '>';
            var si = document.createElement('label');
            si.setAttribute('id','si');
            si.setAttribute('data-type','none');
            si.setAttribute('title','Starting Index');
            si.setAttribute('class',c);
            si.innerHTML = '1';
            
            var s = document.createElement('label');
            s.innerHTML=' - ';
            var ei = document.createElement('label');
            ei.setAttribute('id','ei');
            ei.setAttribute('data-type','none');
            ei.setAttribute('title','Starting Index');
            ei.setAttribute('class',c);
            ei.innerHTML = diff;
            
            ele.appendChild(l);
            ele.appendChild(si);
            ele.appendChild(s);
            ele.appendChild(ei);
            ele.appendChild(r);
        }
    }
};

//history manipulation

//handling the back button press
window.onpopstate = function (event) {
    loadForState(event.state)             
};

function init () {
    loadForState(history.state);
}

//handling with browser reload case
window.addEventListener ? addEventListener("load", init, false) : window.attachEvent ? attachEvent("onload", init) : (onload = init);

function convertQueryParamToJSON(query){
    if(query == null || query.trim().length == 0){
        return {};
    }
    var json = {};
    var segments = query.split("&");
    for(var i=0; i<segments.length; i++){
        var keyValue = segments[i].split("=");
        json[keyValue[0]] = keyValue[1];
    }
    return json;
}

function loadForState(data){
    if(data == null){
        resetPage();
    }else if(data.url == "execute.jsp"){
        RH.server.get(data.url, data.parameter, data.target, callback, true)
        var json = convertQueryParamToJSON(data.parameter);
        RH.g("tables").value = json["table"];
    }else{
        RH.server.get(data.url, data.parameter, data.target, RH.load, true);
    }
}
