export const targetClasses=Object.freeze(['person','cup','bottle','book']);
const overlap=(a,b)=>{const w=Math.max(0,Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x)),h=Math.max(0,Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y));return w*h/(a.w*a.h+b.w*b.h-w*h);};
export function createTargetTracker(epoch=crypto.randomUUID().replaceAll('-','').slice(0,12)) {
  let serial=0,previous=[],last=-Infinity,frameKey=null;
  return {
    clear(){previous=[];last=-Infinity;frameKey=null;},
    observe(predictions,width,height,frame,ts) {
      const time=Date.parse(ts),key=JSON.stringify(frame);
      if(!Number.isFinite(time)||!Number.isFinite(width)||!Number.isFinite(height)||width<=0||height<=0)throw new Error('Invalid target capture');
      if(time<=last||time-last>1500||key!==frameKey)previous=[];
      const boxes=(predictions||[]).filter(d=>targetClasses.includes(d?.class)&&Number.isFinite(d.score)&&d.score>=.5&&d.score<=1&&Array.isArray(d.bbox)&&d.bbox.length===4&&d.bbox.every(Number.isFinite))
        .map(d=>({type:d.class,x:Math.max(0,d.bbox[0]/width),y:Math.max(0,d.bbox[1]/height),w:Math.min(1,(d.bbox[0]+d.bbox[2])/width)-Math.max(0,d.bbox[0]/width),h:Math.min(1,(d.bbox[1]+d.bbox[3])/height)-Math.max(0,d.bbox[1]/height),score:d.score}))
        .filter(d=>d.w>0&&d.h>0).sort((a,b)=>b.score-a.score).slice(0,16);
      const possible=boxes.map(b=>previous.filter(p=>p.type===b.type&&(overlap(p,b)>.2||Math.hypot(p.x+p.w/2-b.x-b.w/2,p.y+p.h/2-b.y-b.h/2)<.08)));
      const next=boxes.map((b,i)=>{
        const crowded=boxes.some((other,j)=>j!==i&&other.type===b.type&&overlap(other,b)>.2);
        const candidates=possible[i],match=!crowded&&candidates.length===1&&possible.filter(list=>list.includes(candidates[0])).length===1?candidates[0]:null;
        // A crossing/occlusion retires identity instead of transferring a user's label.
        return {...b,id:match?.id||`t${epoch}-${++serial}`};
      });
      previous=next;last=time;frameKey=key;
      return {schemaVersion:1,observationId:`s${epoch}-${serial}-${time}`,source:'valerian.scene.camera',ts,frame,
        targets:next.map(b=>({id:b.id,revision:1,label:b.type,type:b.type,provenance:'detected',frame,
          position:{x:b.x+b.w/2,y:b.y+b.h/2,z:0},extent:{x:b.w,y:b.h,z:0},observedAt:ts,expiresAt:new Date(time+3000).toISOString()}))};
    },
  };
}
