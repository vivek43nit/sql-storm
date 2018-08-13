/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
var MySQl = {
    "table": {
        "bindOnRows": function (tableId, event, fn) {
            var rows = document.getElementById(tableId).getElementsByTagName('tr');
            for (var i = 1; i < rows.length; i++) {
                rows[i].addEventListener(event, function () {
                    fn(this)
                }, false);
                rows[i].style.cursor = 'pointer';
            }
        }
    },
    "getFilterDiv": function (value) {
        var ele = document.createElement('div');

        //Adding a checkbox
        var checkBox = document.createElement('input');
        checkBox.setAttribute('type', 'checkbox');
        checkBox.setAttribute('name', 'filter');
        checkBox.setAttribute('value', value);
        checkBox.setAttribute('id', 'id' + value);
        checkBox.setAttribute('onchange', 'MySQl.filterClicked(this)');
        checkBox.setAttribute('style', 'cursor:pointer');

        //Adding label
        var label = document.createElement('label');
        label.setAttribute('for', 'id' + value)
        label.setAttribute('style', 'cursor:pointer');
        label.innerHTML = value;

        //Adding hidden input field
        var inp = document.createElement('input');
        inp.setAttribute('type', 'text');
        inp.setAttribute('style', 'display:none');

        ele.appendChild(checkBox);
        ele.appendChild(label);
        ele.appendChild(inp);
        return ele;
    },
    "filterClicked": function (ele) {
        var childs = ele.parentNode.children;
        console.log(childs[0].checked);
        if (childs[0].checked == true)
            childs[2].style.display = 'block';
        else
            childs[2].style.display = 'none';
    },
    "getRowDataAsJson" : function(tr){
        //ele is tr
        var th = tr.parentNode.children[0].cells; // pointing to table header row
        tr = tr.cells;
        var json = {};
        for(var i=1; i< th.length; i++){
            json[th[i].innerHTML] = tr[i].innerHTML;
        }
        return json;
    },
    "traceRow" : function(db, t, ele){
        var query = 'database='+ db + '&table=' + t +"&row="+RH.enc(JSON.stringify(MySQl.getRowDataAsJson(ele.parentNode)));
        RH.server.get('traceRow.jsp', query, 'rightSegment', RH.load);
    },
    "getCurrentTableEle" : function(ele){
        while(ele != null && ele.nodeName != "TABLE"){
            ele = ele.parentNode;
        }
        return ele;
    },
    "handleReference" : function(ele){
        if(!ele.classList.contains("link")){
            return;
        }
        var tableNode = MySQl.getCurrentTableEle(ele);
        var containerDiv = tableNode.parentNode;
        var query = RH.validation.getUrlByClass(containerDiv, "data", "name", console.log);
        if(query == null){
            return;
        }
        query+= '&column='+ele.getAttribute("name");
        query+= "&row="+RH.enc(JSON.stringify(MySQl.getRowDataAsJson(ele.parentNode)));
        query+= "&append=false&includeSelf=true";
        if(ele.classList.contains("referedBy")){
            RH.server.get('getReferences.jsp', query, 'rightSegment', RH.load);
        }else{
            RH.server.get('getDeReferences.jsp', query, 'rightSegment', RH.load);
        }
    },
    "getEditDiv": function (title, oldData, isNullable) {
        var d = document.createElement('div');
        var l = document.createElement('label');
        l.setAttribute('style', 'min-width:200px;display:inline-block;');
        l.innerHTML = title + ' : ';

        var i = document.createElement('input');
        i.setAttribute('type', 'text');
        i.setAttribute('class', 'data');
        i.setAttribute('title', title);
        i.setAttribute('data-type', 'none');
        i.setAttribute('id', title);
        if (oldData != null) {
            i.setAttribute('value', oldData);
        }
        if(isNullable != null){
            i.setAttribute('data-nullable','1');
        }
        d.appendChild(l);
        d.appendChild(i);
        console.log(isNullable);
        if (isNullable == null || isNullable == "0")
        {
            var c = document.createElement('label');
            c.setAttribute('style', 'color:red');
            c.innerHTML = '*';
            d.appendChild(c);
        }
        return d;
    },
    "getSaveButton": function (title, dataClass, url, target) {
        var b = document.createElement('input');
        b.setAttribute('type', 'button');
        b.setAttribute('value', title);
        b.setAttribute('data-class', dataClass);
        b.setAttribute('data-url', url);
        b.setAttribute('data-target', target);
        b.setAttribute('onclick', 'RH.menu.onClick(this,MySQl.responseHandler,true)');
        return b;
    },
    "getHiddenInfoDiv": function (tableIndex, rowIndex) {
        var t = document.createElement('div');
        var ti = document.createElement('input');
        ti.setAttribute('type', 'hidden');
        ti.setAttribute('value', tableIndex);
        ti.setAttribute('id', 'ti');
        ti.setAttribute('title', 'ti');
        ti.setAttribute('data-type', 'none');
        ti.setAttribute('class', 'data');

        var ri = document.createElement('input');
        ri.setAttribute('type', 'hidden');
        ri.setAttribute('value', rowIndex);
        ri.setAttribute('id', 'ri');
        ri.setAttribute('title', 'ri');
        ri.setAttribute('data-type', 'none');
        ri.setAttribute('class', 'data');
        t.appendChild(ti);
        t.appendChild(ri);
        return t;
    },
    "editRow": function (ele) {
        //ele is td
        var tr = ele.parentNode; //  pointing to whole row
        var th = tr.parentNode.children[0]; // pointing to table header row

        tr = tr.cells;
        th = th.cells;

        var divDialog = RH.g('divDialog');
        var dialogData = RH.g('dialogData');
        dialogData.innerHTML = '';

        var innerDiv = document.createElement('div');
        innerDiv.setAttribute('style', 'width:70%;margin:0 auto;padding:20px;');

        innerDiv.appendChild(MySQl.getHiddenInfoDiv(th[0].getAttribute('data-number'), ele.parentNode.rowIndex));
        for (var i = 1; i < tr.length; i++) {
            innerDiv.appendChild(MySQl.getEditDiv(th[i].innerHTML, tr[i].innerHTML , th[i].getAttribute('data-nullable')))
        }
        innerDiv.appendChild(MySQl.getSaveButton('Save', 'data', 'editRow.jsp', 'dialogMessage'));

        dialogData.appendChild(innerDiv);
        divDialog.style.display = 'block';
    },
    "addRow": function (ele) {
        //ele is td
        var th = ele.parentNode.cells;

        var divDialog = RH.g('divDialog');
        var dialogData = RH.g('dialogData');
        dialogData.innerHTML = '';
        var innerDiv = document.createElement('div');
        innerDiv.setAttribute('style', 'width:70%;margin:0 auto;padding:20px;');

        innerDiv.appendChild(MySQl.getHiddenInfoDiv(th[0].getAttribute('data-number'), ele.parentNode.rowIndex));
        for (var i = 1; i < th.length; i++) {
            innerDiv.appendChild(MySQl.getEditDiv(th[i].innerHTML,null,th[i].getAttribute('data-nullable')))
        }
        innerDiv.appendChild(MySQl.getSaveButton('Add', 'data', 'addRow.jsp', 'dialogMessage'));
        dialogData.appendChild(innerDiv);
        divDialog.style.display = 'block';
    },
    "deleteRow": function (ele) {
        //ele is td
        var tr = ele.parentNode; //  pointing to whole row
        var th = tr.parentNode.children[0].cells;
        var divDialog = RH.g('divDialog');
        var dialogData = RH.g('dialogData');
        dialogData.innerHTML = "Do you want to delete this row ? <input type='button' value='Yes' onclick=\"RH.server.get('deleteRow.jsp','ti=" + th[0].getAttribute('data-number') + "&ri=" + tr.rowIndex + "','dialogMessage',MySQl.responseHandler, true)\"/>";
        divDialog.style.display = 'block';
    },
    "responseHandler": function (target, response) {
        alert(response);
        RH.g('dialogMessage').innerHTML = '';
        RH.g('divDialog').style.display = 'none';
    }
};
function selectTable(ele, isResetFilter) {
    var sLimit = RH.g('limitStart').value;
    var eLimit = RH.g('limitEnd').value;
    
    var group = RH.g('group').value;
    var database = RH.g('database').value;
    
    var orderBy = RH.g('orderBy').value;
    var order = RH.g('order').value;
    
    if (isResetFilter == null)
        isResetFilter = true;

    var filters = document.querySelectorAll('input[name=filter]:checked');
    var whereClause = '';

    if (isResetFilter != true && filters.length > 0) {
        whereClause = ' where ' + filters[0].value + "='" + filters[0].parentNode.children[2].value + "' ";
        for (var i = 1; i < filters.length; i++) {
            whereClause += "and " + filters[i].value + "='" + filters[i].parentNode.children[2].value + "' ";
        }
    }
    var orderByQuery = "";
    if(orderBy != null && orderBy.length > 0){
        orderByQuery = " order by "+orderBy+" "+order;
    }
    var query = "select * from " + ele.value + whereClause +orderByQuery+ " limit " + sLimit + "," + (eLimit - sLimit);
    
    var par = "group="+group
            +"&database="+database
            +"&table="+ele.value+
            "&queryType=S&query=" + query;
    
    if (isResetFilter)
    {
        RH.server.get('execute.jsp', par, 'rightSegment', callback);
    }
    else {
        RH.server.get('execute.jsp', par, 'rightSegment', RH.load);
    }
}
function callback(target, response) {
    RH.load(target, response);
    var filters = RH.g(target).getElementsByTagName('th');
    var filterDiv = RH.g('filters');
    filterDiv.innerHTML = '';
    for (var i = 1; i < filters.length; i++) {
        filterDiv.appendChild(MySQl.getFilterDiv(filters[i].innerHTML));
    }
}
function resetPage(){
    RH.g("filters").innerHTML = "Please Click on a Table";
    RH.g("rightSegment").innerHTML = "";
    if(RH.g("tables")){
        RH.g("tables").value="";
    }
}

function breakLong(val) {
    return [val & 0xFFFFFFFF, Math.floor(val / 0x100000000)];
}
function getLongValue(arr) {
    return arr[1] * Math.pow(2, 16) + arr[0];
}
function ip2num(s, t)
{
    var ip = s.value;
    if (!RH.validation.validateIP(ip))
    {
        return;
    }
    var ipl = [0, 0];
    var ips = ip.split('.');
    console.log(ips);
    ipl[1] += eval(ips[0]);
    ipl[1] <<= 8;
    ipl[1] += eval(ips[1]);

    ipl[0] += eval(ips[2]);
    ipl[0] <<= 8;
    ipl[0] += eval(ips[3]);
    console.log(ipl);
    RH.g(t).value = getLongValue(ipl);
}
function num2ip(s, t)
{
    var num = s.value;
    var d = num % 256;
    for (var i = 3; i > 0; i--)
    {
        num = Math.floor(num / 256);
        d = num % 256 + '.' + d;
    }
    RH.g(t).value = d;
}
function getTimeInMills(e)
{
    var d = new Date();
    d.setTime(d.getTime() - diffInMills + d.getTimezoneOffset() * 60000);
    var t = d.getTime();
    RH.g(e).innerHTML = t;
}