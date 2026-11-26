import * as T from './vendor/three.module.js';

// Width, opening and corner lift share a gently friendly resting shape.
// Expression intensity interpolates from this baseline, including surprise.
const neutralMouth={width:0.1,opening:0.012,curve:0.02};
const expressionMouth={
  happy:{width:0.12,opening:0.013,curve:0.06},
  curious:{width:0.105,opening:0.012,curve:0.04},
  sad:{width:0.1,opening:0.012,curve:-0.06},
  concerned:{width:0.1,opening:0.012,curve:-0.04},
  attentive:{width:0.09,opening:0.012,curve:0},
  uncertain:{width:0.09,opening:0.012,curve:0},
  angry:{width:0.09,opening:0.012,curve:0},
  surprised:{width:0.07,opening:0.09,curve:0},
};

// Original character geometry and articulated rig. No remote textures or assets.
export function createRobot() {
  const root = new T.Group();
  const shell = new T.MeshPhysicalMaterial({color:0xf1e7d5, roughness:0.32, metalness:0.12, clearcoat:0.45});
  const faceMaterial = new T.MeshPhysicalMaterial({color:0xe9ddc8, roughness:0.46, metalness:0.05});
  const joint = new T.MeshStandardMaterial({color:0x29363c, roughness:0.43, metalness:0.48});
  const gold = new T.MeshPhysicalMaterial({color:0xd9a255, roughness:0.34, metalness:0.48});
  const white = new T.MeshStandardMaterial({color:0xfffcf3, roughness:0.26});
  const ink = new T.MeshStandardMaterial({color:0x132d32, roughness:0.23});
  const irisMaterial = new T.MeshPhysicalMaterial({color:0x168e98,roughness:0.22,metalness:0.3,clearcoat:0.8});
  const light = new T.MeshBasicMaterial({color:0xe6ffff});
  const mesh = (parent, geometry, material, position=[0,0,0], scale=[1,1,1]) => {
    const m=new T.Mesh(geometry,material);m.position.set(...position);m.scale.set(...scale);
    m.castShadow=true;m.receiveShadow=true;parent.add(m);return m;
  };
  const sphere=(p,m,pos,scale)=>mesh(p,new T.SphereGeometry(1,32,24),m,pos,scale);
  const rounded=(p,w,h,d,r,m,pos=[0,0,0])=>{
    const s=new T.Shape(),x=-w/2,y=-h/2;
    s.moveTo(x+r,y);s.lineTo(x+w-r,y);s.quadraticCurveTo(x+w,y,x+w,y+r);
    s.lineTo(x+w,y+h-r);s.quadraticCurveTo(x+w,y+h,x+w-r,y+h);
    s.lineTo(x+r,y+h);s.quadraticCurveTo(x,y+h,x,y+h-r);
    s.lineTo(x,y+r);s.quadraticCurveTo(x,y,x+r,y);
    const bevel=Math.min(d*0.25,r*0.5);
    const g=new T.ExtrudeGeometry(s,{depth:d-2*bevel,bevelEnabled:true,bevelThickness:bevel,
      bevelSize:bevel,bevelSegments:5,steps:1,curveSegments:16});
    g.translate(0,0,-(d-2*bevel)/2);return mesh(p,g,m,pos);
  };
  const stroke=(p,points,r,m)=>mesh(p,new T.TubeGeometry(new T.CatmullRomCurve3(points.map(a=>new T.Vector3(...a))),24,r,8,false),m);
  const body=new T.Group();root.add(body);
  sphere(body,shell,[0,0.88,0],[0.49,0.57,0.32]);
  rounded(body,0.65,0.66,0.065,0.17,faceMaterial,[0,0.94,0.27]);
  rounded(body,0.56,0.3,0.45,0.12,joint,[0,0.31,0]);
  rounded(body,0.8,0.35,0.53,0.14,shell,[0,0.14,0]);
  // A quiet chest indicator and neck give scale without decorative dashboards.
  rounded(body,0.12,0.025,0.014,0.01,gold,[0,1.13,0.36]);
  mesh(body,new T.CylinderGeometry(0.16,0.2,0.27,32),joint,[0,1.55,0]);
  const head=new T.Group();head.position.set(0,1.93,0);body.add(head);
  rounded(head,1.15,0.9,0.65,0.23,shell);
  rounded(head,1.01,0.74,0.12,0.2,faceMaterial,[0,-0.02,0.37]);
  for(const side of [-1,1]) {
    sphere(head,joint,[side*0.62,0,0],[0.055,0.2,0.19]);
    sphere(head,gold,[side*0.658,0,0.01],[0.028,0.15,0.145]);
  }
  const eyes=[],brows=[];
  for(const side of [-1,1]) {
    const eye=new T.Group();eye.position.set(side*0.27,0.06,0.46);head.add(eye);
    sphere(eye,faceMaterial,[0,0,-0.015],[0.222,0.27,0.075]);
    sphere(eye,white,[0,0,0],[0.204,0.248,0.085]);
    const pupil=new T.Group();eye.add(pupil);
    sphere(pupil,irisMaterial,[0,-0.018,0.078],[0.132,0.173,0.028]);
    sphere(pupil,ink,[0,-0.012,0.103],[0.07,0.108,0.018]);
    sphere(pupil,light,[-0.043,0.063,0.122],[0.027,0.035,0.009]);
    sphere(pupil,light,[0.04,-0.08,0.113],[0.012,0.015,0.006]);
    eyes.push({eye,pupil});
    const brow=stroke(head,[[-0.16,0,0],[0,0.045,0],[0.16,0.005,0]],0.025,joint);
    brow.position.set(side*0.27,0.357,0.457);brows.push(brow);
  }
  const mouth=new T.Group();mouth.position.set(0,-0.25,0.467);head.add(mouth);
  const lip=sphere(mouth,ink,[0,0,0],[1,1,1]);lip.name='mouth';
  const mouthPosition=lip.geometry.attributes.position, mouthRest=mouthPosition.array.slice();
  let previousFace,previousIntensity;
  function applyMouth(face,intensity) {
    if(face===previousFace&&intensity===previousIntensity)return;
    previousFace=face;previousIntensity=intensity;
    const target=Object.hasOwn(expressionMouth,face)?expressionMouth[face]:neutralMouth;
    const width=neutralMouth.width+(target.width-neutralMouth.width)*intensity;
    const opening=neutralMouth.opening+(target.opening-neutralMouth.opening)*intensity;
    const curve=neutralMouth.curve+(target.curve-neutralMouth.curve)*intensity;
    // Bend a rounded lip without scaling its thickness through zero or swapping
    // meshes. Reuse its vertices; ordinary animation frames allocate no geometry.
    for(let i=0;i<mouthPosition.count;i++) {
      const x=mouthRest[i*3],y=mouthRest[i*3+1],z=mouthRest[i*3+2];
      mouthPosition.setXYZ(i,x*width,y*opening+curve*(x*x-0.5),z*0.012);
    }
    mouthPosition.needsUpdate=true;lip.geometry.computeVertexNormals();
    lip.geometry.computeBoundingSphere();
  }

  function arm(side) {
    const name=side===-1?'right':'left';
    const shoulder=new T.Group();shoulder.name=name+'.shoulder';shoulder.position.set(side*0.58,1.29,0);body.add(shoulder);
    sphere(shoulder,joint,[0,0,0],[0.19,0.19,0.19]);
    sphere(shoulder,gold,[side*0.035,-0.05,0.025],[0.22,0.215,0.205]);
    rounded(shoulder,0.25,0.44,0.27,0.09,shell,[0,-0.32,0]);
    const elbow=new T.Group();elbow.position.y=-0.6;shoulder.add(elbow);
    sphere(elbow,joint,[0,0,0],[0.14,0.14,0.14]);
    rounded(elbow,0.25,0.38,0.27,0.09,shell,[0,-0.25,0]);
    const wrist=new T.Group();wrist.position.y=-0.49;elbow.add(wrist);
    sphere(wrist,joint,[0,0,0],[0.095,0.09,0.095]);
    const hand=new T.Group();hand.position.y=-0.14;hand.scale.setScalar(1.3);wrist.add(hand);
    rounded(hand,0.24,0.24,0.12,0.055,shell);
    rounded(hand,0.18,0.15,0.018,0.04,faceMaterial,[0,-0.015,0.075]);
    const fingers=[];
    for(let i=0;i<4;i++) {
      const finger=new T.Group();finger.position.set((i-1.5)*0.065,-0.11,0);hand.add(finger);
      finger.name=name+'.'+['index','middle','ring','little'][side===-1?3-i:i];
      const length=[0.09,0.105,0.097,0.074][i],joints=[];let parent=finger;
      for(let j=0;j<3;j++) {
        const knuckle=new T.Group();knuckle.name=finger.name+'.'+j;if(j)knuckle.position.y=-length;parent.add(knuckle);
        sphere(knuckle,joint,[0,0,0],[0.029,0.029,0.029]);
        sphere(knuckle,shell,[0,-length*0.52,0],[0.031,length*0.61,0.034]);
        joints.push(knuckle);parent=knuckle;
      }
      fingers.push({finger,joints});
    }
    const thumb=new T.Group();thumb.position.set(-side*0.14,0.02,0);thumb.rotation.z=-side*0.65;hand.add(thumb);
    const thumbJoints=[];let parent=thumb;
    for(let j=0;j<2;j++) {
      const k=new T.Group();if(j)k.position.y=-0.085;parent.add(k);
      sphere(k,joint,[0,0,0],[0.035,0.035,0.035]);sphere(k,shell,[0,-0.05,0],[0.038,0.058,0.04]);
      thumbJoints.push(k);parent=k;
    }
    return {side,shoulder,elbow,wrist,hand,fingers,thumb,thumbJoints};
  }
  // Robot right is viewer left, matching a face-to-face encounter.
  const arms=[arm(-1),arm(1)];
  function apply(sample,time=0) {
    const p=sample.pose,g=sample.gesture*(0.8+0.3*(p?.energy||0)),ambient=sample.ambient;
    body.position.y=Math.sin(time*0.0018)*0.012*ambient;
    const face=p?.face||'neutral', intensity=p?.intensity||0;
    head.rotation.set(p?.gaze[1]||0,p?.gaze[0]||0,face==='curious'||face==='uncertain'?0.09*intensity:0);
    if(p?.gesture==='ACKNOWLEDGE')head.rotation.x+=Math.sin(g*Math.PI)*0.14;
    const blink=ambient>0 && time%5100>4920 ? 0.12 : 1;
    eyes.forEach(({eye,pupil})=>{
      eye.scale.y=blink*(face==='happy'?1-0.2*intensity:face==='surprised'?1+0.1*intensity:1);
      pupil.position.x=(p?.gaze[0]||0)*0.1;pupil.position.y=-(p?.gaze[1]||0)*0.12;
    });
    brows.forEach((b,i)=>{
      b.rotation.z=(i===0?1:-1)*(['sad','concerned','uncertain'].includes(face)?0.3*intensity:face==='angry'?-0.4*intensity:0);
      b.position.y=0.357+(face==='surprised'?0.05*intensity:0);
    });
    applyMouth(face,intensity);
    for(const a of arms) {
      a.shoulder.rotation.set(-0.08,0,a.side*0.12);a.elbow.rotation.set(-0.18,0,0);a.wrist.rotation.set(0,0,0);
      const sign=p?.sign && (p.hand==='both_hands'||p.hand===(a.side===-1?'right_hand':'left_hand'))?p.sign:'';
      if(sign) {
        const t=sample.transition??1;
        a.shoulder.rotation.set(-0.08-0.07*t,0,a.side*(0.12+0.73*t));a.elbow.rotation.x=-0.18-1.92*t;
        a.wrist.rotation.set(-1.05*t,Math.PI*t,-a.side*0.85*t);
      } else if(p) {
        const gesture=p.gesture;
        if(gesture==='OPEN_QUESTION'||gesture==='UNCERTAIN') {
          a.shoulder.rotation.z=a.side*(0.12+0.38*g);a.elbow.rotation.x=-0.18-1.4*g;
          a.wrist.rotation.z=-a.side*0.55*g;
        } else if(gesture==='EXPLAIN'&&a.side===-1) {
          a.shoulder.rotation.z=-0.12-0.45*g;a.elbow.rotation.x=-0.18-1.45*g;
        } else if(gesture==='RIGHT_HAND_UP'&&a.side===-1) {
          a.shoulder.rotation.z=-0.12-0.85*g;a.elbow.rotation.x=-0.18-1.9*g;
        }
      }
      a.fingers.forEach(({finger,joints},i)=>{
        const folded=sign==='rock'||sign==='scissor'&&i>1;
        // Index is nearest the thumb, which is mirrored with the hand.
        const index=a.side===-1?3-i:i;
        const curl=sign==='scissor'?(index<2?0:1.5):folded?1.5:sign==='paper'?0:0.2;
        finger.rotation.z=sign==='scissor'&&index<2?(index===0?-a.side*0.13:a.side*0.13):0;
        joints.forEach((j,n)=>j.rotation.x=curl*(n===0?0.9:1));
      });
      a.thumb.rotation.z=-a.side*(sign==='paper'?0.95:sign?0.3:0.65);
      a.thumbJoints.forEach(j=>j.rotation.x=sign&&sign!=='paper'?1.0:0.1);
    }
  }
  apply({pose:null,gesture:0,ambient:0});
  return {root,apply};
}

export class RobotScene {
  constructor(canvas) {
    this.renderer=new T.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'low-power'});
    this.renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));
    this.renderer.outputColorSpace=T.SRGBColorSpace;this.renderer.toneMapping=T.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure=1.25;
    this.scene=new T.Scene();this.camera=new T.OrthographicCamera(-1.8,1.8,2,-2,0.1,30);
    this.camera.position.set(0.4,1.6,8);this.camera.lookAt(0,1.1,0);
    this.scene.add(new T.HemisphereLight(0xf1f8ff,0x687481,2.2));
    const key=new T.DirectionalLight(0xffe8cc,3.3);key.position.set(-3,5,5);this.scene.add(key);
    const rim=new T.DirectionalLight(0xa4dbed,2);rim.position.set(4,2,-2);this.scene.add(rim);
    this.robot=createRobot();this.scene.add(this.robot.root);
    this.renderer.setClearColor(0,0);
  }
  resize(width,height) {
    if(width<1||height<1)return;
    this.renderer.setSize(width,height,false);
    const halfHeight=Math.max(1.55,1.5*height/width),halfWidth=halfHeight*width/height;
    Object.assign(this.camera,{left:-halfWidth,right:halfWidth,top:halfHeight,bottom:-halfHeight});
    this.camera.updateProjectionMatrix();
  }
  draw(sample,time) {this.robot.apply(sample,time);this.renderer.render(this.scene,this.camera);}
  dispose() {
    const geometries=new Set(),materials=new Set();
    this.scene.traverse(o=>{if(o.geometry)geometries.add(o.geometry);if(o.material)materials.add(o.material);});
    geometries.forEach(g=>g.dispose());materials.forEach(m=>m.dispose());this.renderer.dispose();
  }
}
