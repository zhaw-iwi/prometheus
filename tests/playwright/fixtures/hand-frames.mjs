// Retain the first actual holding frame for each command while native conversation continues.
export function captureHandFrames() {
  const stage=document.querySelector('[data-testid="embodiment-stage"]');
  const frames=new Map();
  window.__handFrames=frames;
  const observer=new MutationObserver(()=>{
    const {handEvent,handPhase,sequenceStatus}=stage.dataset;
    if(!handEvent||handPhase!=='holding'||sequenceStatus||frames.has(handEvent)||frames.size>=16)return;
    const snapshot=ValerianEmbodiment.snapshot();
    if(snapshot?.phase!=='holding')return;
    const gl=document.getElementById('embodiment_canvas').getContext('webgl2');
    const pixels=new Uint8Array(gl.drawingBufferWidth*gl.drawingBufferHeight*4);
    gl.readPixels(0,0,gl.drawingBufferWidth,gl.drawingBufferHeight,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
    let irisPixels=0;
    for(let i=0;i<pixels.length;i+=4){
      if(pixels[i+3]>0&&pixels[i+1]>70&&pixels[i+2]>70&&pixels[i+1]>pixels[i]*1.5&&pixels[i+2]>pixels[i]*1.5)irisPixels++;
    }
    frames.set(handEvent,{...snapshot,irisPixels,glError:gl.getError()});
  });
  observer.observe(stage,{attributes:true,attributeFilter:['data-hand-event','data-hand-phase']});
}
