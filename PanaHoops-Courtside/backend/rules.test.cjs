const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,where,updateDoc,deleteDoc,arrayUnion,arrayRemove,serverTimestamp}=require('firebase/firestore');
const fs=require('fs');
(async()=>{
 const env=await initializeTestEnvironment({projectId:'demo-panahoops',firestore:{rules:fs.readFileSync('firestore.rules','utf8')}});
 const alice=env.authenticatedContext('alice').firestore(),bob=env.authenticatedContext('bob').firestore(),eve=env.authenticatedContext('eve').firestore(),anon=env.unauthenticatedContext().firestore();
 const id='0123456789abcdef0123456789abcdef',g=db=>doc(db,'groups',id),msg=db=>doc(db,'groups',id,'messages','m1'),stats=(db,uid)=>doc(db,'groups',id,'stats',uid);
 let count=0;async function test(name,task){await task();count++;console.log('PASS',name);}
 try{
 await test('create own group',()=>assertSucceeds(setDoc(g(alice),{name:'Equipo',owner:'alice',members:['alice'],created:serverTimestamp()})));
 await test('unauthenticated cannot read invitation',()=>assertFails(getDoc(g(anon))));
 await test('outsider cannot list groups',()=>assertFails(getDocs(collection(eve,'groups'))));
 await test('member can query own groups',()=>assertSucceeds(getDocs(query(collection(alice,'groups'),where('members','array-contains','alice')))));
 await test('invited authenticated user can join',()=>assertSucceeds(updateDoc(g(bob),{members:arrayUnion('bob')})));
 await test('cannot add someone else',()=>assertFails(updateDoc(g(bob),{members:arrayUnion('eve')})));
 await test('cannot replace owner',()=>assertFails(updateDoc(g(bob),{owner:'bob'})));
 await test('member can send message',()=>assertSucceeds(setDoc(msg(alice),{author:'alice',name:'A',text:'Buen entrenamiento',created:serverTimestamp()})));
 await test('outsider cannot read messages',()=>assertFails(getDoc(msg(eve))));
 await test('member cannot impersonate author',()=>assertFails(setDoc(doc(bob,'groups',id,'messages','spoof'),{author:'alice',name:'A',text:'fake',created:serverTimestamp()})));
 await test('member can publish own stats',()=>assertSucceeds(setDoc(stats(bob,'bob'),{name:'B',attempts:10,made:5,sessions:2,updated:serverTimestamp()})));
 await test('cannot overwrite other stats',()=>assertFails(setDoc(stats(alice,'bob'),{name:'A',attempts:10,made:6,sessions:2,updated:serverTimestamp()})));
 await test('reject impossible stats',()=>assertFails(setDoc(stats(bob,'bob'),{name:'B',attempts:10,made:11,sessions:2,updated:serverTimestamp()})));
 await test('cannot delete someone else message',()=>assertFails(deleteDoc(msg(bob))));
 await test('leave group',()=>assertSucceeds(updateDoc(g(bob),{members:arrayRemove('bob')})));
 await test('former member loses message access',()=>assertFails(getDoc(msg(bob))));
 await test('former member can remove own stats',()=>assertSucceeds(deleteDoc(stats(bob,'bob'))));
 console.log(`RULES_TESTS_PASSED=${count}`);
 }finally{await env.cleanup();}
})().catch(e=>{console.error(e);process.exit(1)});
