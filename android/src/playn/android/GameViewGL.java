/**
 * Copyright 2011 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package playn.android;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

public class GameViewGL extends GLSurfaceView {

  private final AndroidPlatform plat;
  private AtomicBoolean started = new AtomicBoolean(false);
  private AtomicBoolean paused = new AtomicBoolean(true);

  public GameViewGL(Context context, AndroidPlatform platform) {
    super(context);
    this.plat = platform;

    setFocusable(true);
    setEGLContextClientVersion(2);
    setPreserveEGLContextOnPause(true);

    // set up our renderer
    setRenderer(new Renderer() {
      @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        plat.graphics().onSurfaceCreated();
      }
      @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
        plat.graphics().onSizeChanged(width, height);
        // we defer the start of the game until we've received our initial surface size
        if (!started.get()) startGame();
      }
      @Override public void onDrawFrame(GL10 gl) {
        if (!paused.get()) plat.processFrame();
      }
    });
    setRenderMode(RENDERMODE_CONTINUOUSLY);
  }

  @Override public void onPause() {
    // pause our game updates
    paused.set(true);

    // TODO: no longer needed?
    // this is a terribly unfortunate hack; we would like to override surfaceDestroyed and indicate
    // that our surface was lost only when that method was called, but surfaceDestroyed is not
    // called when the screen is locked while our game is running (even though we do in fact lose
    // our GL context at that point); so we have to assume that we ALWAYS lose our GL context when
    // paused; that's generally true, so we're probably safe in assuming so, but it sucks
    queueEvent(new Runnable() {
      public void run () { plat.graphics().onSurfaceLost(); }
    });
    super.onPause();
  }

  @Override public void onResume() {
    super.onResume();
    // unpause our game updates
    paused.set(false);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    return plat.input().onTouch(event);
  }

  void startGame() {
    started.set(true);
    queueEvent(new Runnable() {
      @Override
      public void run() {
        plat.activity.main();
        paused.set(false);
      }
    });
  }
}
