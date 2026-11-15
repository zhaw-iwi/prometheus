import {loadVisualModels} from './camera-models.js';

const signs={Thumb_Up:'THUMBS_UP',Thumb_Down:'THUMBS_DOWN',Open_Palm:'OPEN_PALM'};
/** A consumed gesture needs a neutral release, including across question/revision/reconnect boundaries. */
export class SignEdge {
  constructor(){this.reset();}
  reset(){this.armed=false;this.release=0;this.sign=null;this.frames=0;this.scope=null;}
  sample(category,confidence,scope,present=category!=null){
    const id=JSON.stringify(scope);
    if(this.scope!==id){this.scope=id;this.armed=false;this.release=0;this.frames=0;this.sign=null;}
    const sign=confidence>=.65?signs[category]:null;
    if(!present){this.sign=null;this.frames=0;if(++this.release>=2)this.armed=true;return null;}
    this.release=0;
    if(!sign){this.sign=null;this.frames=0;return null;}
    if(this.sign!==sign){this.frames=0;this.sign=sign;}
    if(!scope||!this.armed)return null;
    if(++this.frames<3)return null;
    this.armed=false;return {...scope,sign,confidence,stabilityFrames:this.frames};
  }
}

export class StudyCamera {
  constructor({video,onStatus,onFrame,scope,clock,models=loadVisualModels,media=()=>navigator.mediaDevices.getUserMedia({video:{width:{ideal:640},height:{ideal:480}},audio:false})}){
    Object.assign(this,{video,onStatus,onFrame,scope,clock,models,media});this.epoch=0;this.running=false;this.starting=false;this.edge=new SignEdge();
  }
  async start(){
    if(this.running||this.starting)return;
    const epoch=++this.epoch;this.starting=true;this.onStatus('Requesting camera permission…',false);
    let stream,models;
    try{
      stream=await this.media();
      if(epoch!==this.epoch){stream.getTracks().forEach(t=>t.stop());return;}
      this.stream=stream;this.video.srcObject=stream;await this.video.play();
      if(epoch!==this.epoch)return;
      for(const track of stream.getTracks())track.addEventListener('ended',()=>{if(epoch===this.epoch)this.stop('Camera disconnected. Reconnect it and select Start camera.');},{once:true});
      this.onStatus('Loading face, person and hand detectors…',false);
      models=await this.models();
      if(epoch!==this.epoch){models.close();return;}
      this.detectors=models;this.running=true;this.starting=false;this.edge.reset();this.previousCount=undefined;this.countFrames=0;this.count=null;this.lastVideoTime=-1;this.lastFrameAt=performance.now();
      this.onStatus('Camera and all three detectors are running.',true);void this.loop(epoch);
    }catch(error){
      if(epoch!==this.epoch)return;
      this.stop(error?.name==='NotAllowedError'?'Camera permission was denied. Allow camera access in your browser, then select Start camera.':error?.name==='NotFoundError'?'No camera was found. Connect one and select Start camera.':'Camera or detector startup failed. Check your connection and select Start camera.');
    }
  }
  async loop(epoch){
    if(!this.running||epoch!==this.epoch)return;
    try{
      if(this.video.readyState>=2&&this.video.currentTime!==this.lastVideoTime){
        this.lastVideoTime=this.video.currentTime;this.lastFrameAt=performance.now();const observedAt=this.clock();const scope=this.scope();
        const frame=await this.detectors.sample(this.video);
        if(epoch!==this.epoch)return;
        if(frame.humanCount===this.previousCount)this.countFrames++;else{this.previousCount=frame.humanCount;this.countFrames=1;}
        // Extra people suppress disclosure immediately. Absence/return need three frames.
        if(frame.humanCount>=2||this.countFrames>=3)this.count=frame.humanCount;
        const sign=this.edge.sample(frame.gesture,frame.gestureConfidence,scope,frame.handPresent??frame.gesture!=null);
        this.onFrame({...frame,humanCount:this.count,observedAt,sign});
      }
      else if(performance.now()-this.lastFrameAt>5000){this.stop('The camera stopped supplying frames. Select Start camera to retry.');return;}
    }catch{if(epoch===this.epoch)this.stop('A visual detector stopped. Select Start camera to retry.');return;}
    if(epoch===this.epoch)this.timer=setTimeout(()=>void this.loop(epoch),350);
  }
  stop(message='Camera stopped. Select Start camera to continue.'){
    this.epoch++;this.starting=false;this.running=false;clearTimeout(this.timer);this.stream?.getTracks().forEach(t=>t.stop());this.stream=null;
    this.detectors?.close();this.detectors=null;this.video.srcObject=null;this.edge.reset();this.onStatus(message,false);
  }
}
