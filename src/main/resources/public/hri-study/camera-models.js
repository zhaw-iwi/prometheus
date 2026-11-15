// Same detector families/versions as Valerian. Frames never leave this browser.
const scripts=new Map();
function script(url){
  if(!scripts.has(url))scripts.set(url,new Promise((resolve,reject)=>{
    const element=document.createElement('script');element.src=url;element.crossOrigin='anonymous';
    element.onload=resolve;element.onerror=()=>{scripts.delete(url);element.remove();reject(Error('Detector download failed. Check your connection and retry.'));};document.head.append(element);
  }));return scripts.get(url);
}
export async function loadVisualModels(){
  await script('https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@1.7.4/dist/tf.min.js');
  await script('https://cdn.jsdelivr.net/npm/@tensorflow-models/coco-ssd@2.2.3');
  await script('https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/dist/face-api.min.js');
  const face=window.faceapi;
  await Promise.all([face.nets.tinyFaceDetector.loadFromUri('https://justadudewhohacks.github.io/face-api.js/models'),
    face.nets.faceExpressionNet.loadFromUri('https://justadudewhohacks.github.io/face-api.js/models')]);
  const people=await window.cocoSsd.load({base:'lite_mobilenet_v2'});
  try{
    const vision=await import('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35');
    const files=await vision.FilesetResolver.forVisionTasks('https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35/wasm');
    const hands=await vision.GestureRecognizer.createFromOptions(files,{baseOptions:{modelAssetPath:'https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/gesture_recognizer.task'},runningMode:'VIDEO',numHands:1});
    return {
      async sample(video){
        const detections=await people.detect(video);
        const expression=await face.detectSingleFace(video,new face.TinyFaceDetectorOptions({inputSize:224,scoreThreshold:.45})).withFaceExpressions();
        const [emotion,confidence]=Object.entries(expression?.expressions??{}).sort((a,b)=>b[1]-a[1])[0]??[null,null];
        const handResult=hands.recognizeForVideo(video,performance.now());const gesture=handResult.gestures?.[0]?.[0];
        return {humanCount:Math.min(3,detections.filter(d=>d.class==='person'&&d.score>=.45).length),
          emotion:confidence>=.55?emotion:null,confidence:confidence>=.55?confidence:null,
          gesture:gesture?.categoryName??null,gestureConfidence:gesture?.score??0,handPresent:!!handResult.landmarks?.length};
      },
      close(){hands.close();people.dispose();}
    };
  }catch(error){people.dispose();throw error;}
}
