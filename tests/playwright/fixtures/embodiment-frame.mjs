// Reduced-motion screenshots need a complete GPU frame, without replaying a command.
export async function synchronizeEmbodimentFrame(page) {
  return page.evaluate(() => {
    ValerianEmbodiment.settle();
    const gl = document.getElementById('embodiment_canvas').getContext('webgl2');
    const pixels = new Uint8Array(gl.drawingBufferWidth * gl.drawingBufferHeight * 4);
    gl.readPixels(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
    let irisPixels = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i + 3] > 0 && pixels[i + 1] > 70 && pixels[i + 2] > 70
          && pixels[i + 1] > pixels[i] * 1.5 && pixels[i + 2] > pixels[i] * 1.5) irisPixels++;
    }
    return {irisPixels, glError: gl.getError()};
  });
}
