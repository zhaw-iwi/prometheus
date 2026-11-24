// One delivery per sensor kind; a blocked kind must not age another kind's capture.
export function startObservationPumps(callbacks, wait=ms=>new Promise(resolve=>setTimeout(resolve,ms))) {
  const pump={running:true,error:null};
  pump.done=Promise.all(callbacks.map(async callback=>{
    while(pump.running){
      await wait(500);if(!pump.running)break;
      try{await callback();}catch(error){pump.error??=error;pump.running=false;}
    }
  }));
  return pump;
}
