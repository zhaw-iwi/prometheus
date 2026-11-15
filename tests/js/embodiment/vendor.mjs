// Reproduce the local browser distribution after npm ci. Three.js is MIT licensed.
import {mkdir,copyFile} from 'node:fs/promises';
const destination=new URL('../../../src/main/resources/public/valerian/embodiment/vendor/',import.meta.url);
await mkdir(destination,{recursive:true});
for(const file of ['three.module.js','three.core.js'])await copyFile(new URL('../../../node_modules/three/build/'+file,import.meta.url),new URL(file,destination));
await copyFile(new URL('../../../node_modules/three/LICENSE',import.meta.url),new URL('LICENSE.txt',destination));
