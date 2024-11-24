(self.webpackChunk_N_E=self.webpackChunk_N_E||[]).push([[972],{5728:(e,t,a)=>{Promise.resolve().then(a.bind(a,2475)),Promise.resolve().then(a.bind(a,4906))},2475:(e,t,a)=>{"use strict";a.d(t,{default:()=>d});var n=a(5155),s=a(2115),c=a(4740);let d=()=>{let[e,t]=(0,s.useState)([{id:1,name:"Category1"},{id:2,name:"Category2"},{id:3,name:"Category3"}]);return(0,n.jsx)(c.A,{items:e,onAdd:e=>{t(t=>[...t,...e])},onDelete:e=>{t(t=>t.filter(t=>!e.has(t.id)))},label:"Categories"})}},4740:(e,t,a)=>{"use strict";a.d(t,{A:()=>i});var n=a(5155),s=a(2115),c=a(3478),d=a.n(c),l=a(9939),r=a.n(l);let o=e=>{let{onClose:t,onSave:a,label:c}=e,[d,l]=(0,s.useState)([""]),o=(e,t)=>{let a=[...d];a[e]=t,l(a)};return(0,n.jsx)("div",{className:r().overlay,children:(0,n.jsxs)("div",{className:r().panel,children:[(0,n.jsx)("h2",{children:"Add ".concat(c)}),(0,n.jsx)("div",{className:r().entries,children:d.map((e,t)=>(0,n.jsx)("input",{type:"text",value:e,onChange:e=>o(t,e.target.value),placeholder:"New ".concat(c.slice(0,-1)," ").concat(t+1),className:r().input},t))}),(0,n.jsx)("button",{className:r().addEntryButton,onClick:()=>{l([...d,""])},children:"+ Add Another"}),(0,n.jsxs)("div",{className:r().actions,children:[(0,n.jsx)("button",{className:r().saveButton,onClick:()=>{let e=d.filter(e=>""!==e.trim());if(0===e.length){alert("Please add at least one valid entry.");return}a(e.map((e,t)=>({id:Date.now()+t,name:e}))),t()},children:"Save"}),(0,n.jsx)("button",{className:r().cancelButton,onClick:t,children:"Cancel"})]})]})})},i=e=>{let{items:t,onAdd:a,onDelete:c,onEdit:l,label:r}=e,[i,_]=(0,s.useState)(!1),[u,m]=(0,s.useState)(new Set),[h,C]=(0,s.useState)(!1),x=e=>{i&&m(t=>{let a=new Set(t);return a.has(e)?a.delete(e):a.add(e),a})};return(0,n.jsxs)("div",{className:d().cardContainer,children:[(0,n.jsxs)("div",{className:d().header,children:[(0,n.jsx)("p",{children:r}),(0,n.jsx)("div",{className:d().buttons,children:i?(0,n.jsxs)(n.Fragment,{children:[(0,n.jsx)("button",{className:"".concat(d().confirmButton," ").concat(d().button),onClick:()=>{if(0===u.size){alert("No ".concat(r," selected."));return}window.confirm("Are you sure you want to delete the selected ".concat(r,"?"))&&(c(u),m(new Set),_(!1))},children:"Confirm Delete"}),(0,n.jsx)("button",{className:"".concat(d().cancelButton," ").concat(d().button),onClick:()=>{_(!1),m(new Set)},children:"Cancel"})]}):(0,n.jsxs)(n.Fragment,{children:[(0,n.jsx)("button",{className:"".concat(d().addButton," ").concat(d().button),onClick:()=>C(!0),children:(0,n.jsx)("img",{src:"/svg/add.svg",alt:"Add"})}),l&&(0,n.jsx)("button",{className:"".concat(d().editButton," ").concat(d().button),onClick:l,children:(0,n.jsx)("img",{src:"/svg/edit.svg",alt:"Edit"})}),(0,n.jsx)("button",{className:"".concat(d().removeButton," ").concat(d().button),onClick:()=>{_(!0)},children:(0,n.jsx)("img",{src:"/svg/delete.svg",alt:"Delete"})})]})})]}),(0,n.jsx)("div",{className:d().cardList,children:t.map(e=>(0,n.jsxs)("div",{className:"".concat(d().card," ").concat(u.has(e.id)?d().selected:""),onClick:()=>x(e.id),children:[i&&(0,n.jsx)("label",{className:d().checkboxLabel,children:(0,n.jsx)("input",{type:"checkbox",checked:u.has(e.id),className:d().checkbox})}),(0,n.jsx)("p",{className:d().text,children:e.name})]},e.id))}),h&&(0,n.jsx)(o,{onClose:()=>C(!1),onSave:a,label:r})]})}},4906:(e,t,a)=>{"use strict";a.d(t,{default:()=>d});var n=a(5155),s=a(2115),c=a(4740);let d=()=>{let[e,t]=(0,s.useState)([{id:1,name:"TestName1"},{id:2,name:"TestName2"},{id:3,name:"TestName3"}]);return(0,n.jsx)(c.A,{items:e,onAdd:e=>{t(t=>[...t,...e])},onDelete:e=>{t(t=>t.filter(t=>!e.has(t.id)))},label:"Performers"})}},9939:e=>{e.exports={overlay:"AddPanel_overlay__Euaek",panel:"AddPanel_panel__nDe1e",entries:"AddPanel_entries__2_G84",input:"AddPanel_input__3QrQs",addEntryButton:"AddPanel_addEntryButton__h915M",actions:"AddPanel_actions__7TjRQ",saveButton:"AddPanel_saveButton__csP7w",cancelButton:"AddPanel_cancelButton___yu1n"}},3478:e=>{e.exports={cardContainer:"Card_cardContainer__k2ns8",header:"Card_header__fnHaN",cardList:"Card_cardList__fLTBf",card:"Card_card__2PK1q",buttons:"Card_buttons__r3cXv",text:"Card_text__38G_d",button:"Card_button__MC5_6",editButton:"Card_editButton__GoOKp",removeButton:"Card_removeButton__0C_hC",addButton:"Card_addButton__1vMZZ",icon:"Card_icon__GNAbJ",addForm:"Card_addForm__sLLJr",input:"Card_input__ZJ_dU",saveButton:"Card_saveButton__tutpa",cancelButton:"Card_cancelButton__vk3sj",confirmButton:"Card_confirmButton__tNm1E",checkboxLabel:"Card_checkboxLabel__VgbGh",checkbox:"Card_checkbox__Zm_j3",selected:"Card_selected__Sq1Q1"}}},e=>{var t=t=>e(e.s=t);e.O(0,[516,441,517,358],()=>t(5728)),_N_E=e.O()}]);