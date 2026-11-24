// One delivery per sensor kind; a blocked kind must not age another kind's capture.
export function startObservationPumps(callbacks, wait=ms=>new Promise(resolve=>setTimeout(resolve,ms)), now=()=>Date.now()) {
  const pump={running:true,error:null};
  pump.done=Promise.all(callbacks.map(async callback=>{
    let next=now()+500;
    while(pump.running){
      await wait(Math.max(0,next-now()));if(!pump.running)break;
      // Match camera capture cadence without overlapping a kind's HTTP requests.
      // A slow receipt already spent the interval; do not add another 500 ms.
      next=now()+500;
      try{await callback();}catch(error){pump.error??=error;pump.running=false;}
    }
  }));
  return pump;
}
