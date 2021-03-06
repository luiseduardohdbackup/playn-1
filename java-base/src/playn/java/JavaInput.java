/**
 * Copyright 2010-2015 The PlayN Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package playn.java;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import playn.core.*;
import pythagoras.f.Point;
import react.Slot;

public class JavaInput extends Input {

  protected final JavaPlatform plat;

  // used for injecting keyboard evnets
  private final Deque<Keyboard.Event> kevQueue = new ConcurrentLinkedDeque<>();

  // these are used for touch emulation
  private boolean mouseDown;
  private Point pivot;
  private float x, y;
  private int currentId;

  public JavaInput (JavaPlatform plat) {
    this.plat = plat;

    // if touch emulation is configured, wire it up
    if (plat.config.emulateTouch) emulateTouch();
  }

  /** Posts a key event received from elsewhere (i.e. a native UI component). This is useful for
    * applications that are using GL in Canvas mode and sharing keyboard focus with other (non-GL)
    * components. The event will be queued and dispatched on the next frame, after GL keyboard
    * events.
    *
    * @param time the time (in millis since epoch) at which the event was generated, or 0 if N/A.
    * @param key the key that was pressed or released, or null for a char typed event
    * @param pressed whether the key was pressed or released, ignored if key is null
    * @param typedCh the character that was typed, ignored if key is not null
    */
  public void postKey (long time, Key key, boolean pressed, char typedCh) {
    kevQueue.add(key == null ? new Keyboard.TypedEvent(0, time, typedCh) :
                 new Keyboard.KeyEvent(0, time, key, pressed));
  }

  protected void emulateTouch () {
    final Key pivotKey = plat.config.pivotKey;
    keyboardEvents.connect(new Slot<Keyboard.Event>() {
      public void onEmit (Keyboard.Event event) {
        if (event instanceof Keyboard.KeyEvent) {
          Keyboard.KeyEvent kevent = (Keyboard.KeyEvent)event;
          if (kevent.key == pivotKey && kevent.down) {
            pivot = new Point(x, y);
          }
        }
      }
    });

    mouseEvents.connect(new Slot<Mouse.Event>() {
      public void onEmit (Mouse.Event event) {
        if (event instanceof Mouse.ButtonEvent) {
          Mouse.ButtonEvent bevent = (Mouse.ButtonEvent)event;
          if (bevent.button == Mouse.ButtonEvent.Id.LEFT) {
            if (mouseDown = bevent.down) {
              currentId += 2; // skip an id in case of pivot
              dispatchTouch(event, Touch.Event.Kind.START);
            } else {
              pivot = null;
              dispatchTouch(event, Touch.Event.Kind.END);
            }
          }
        } else if (event instanceof Mouse.MotionEvent) {
          if (mouseDown) dispatchTouch(event, Touch.Event.Kind.MOVE);
          // keep track of the current mouse position for pivot
          x = event.x; y = event.y;
        }
      }
    });

    // TODO: it's pesky that both mouse and touch events are dispatched when touch is emulated, it
    // would be nice to throw away the mouse events and only have touch, but we rely on something
    // generating the mouse events so we can't throw them away just yet... plus it could be useful
    // to keep wheel events... blah
  }

  @Override public boolean hasHardwareKeyboard() { return true; }
  @Override public boolean hasTouch () { return plat.config.emulateTouch; }

  void init () {} // nothing by default

  void update () {
    // dispatch any queued keyboard events
    Keyboard.Event kev;
    while ((kev = kevQueue.poll()) != null) keyboardEvents.emit(kev);
  }

  private void dispatchTouch (Mouse.Event event, Touch.Event.Kind kind) {
    float ex = event.x, ey = event.y;
    Touch.Event main = toTouch(event.time, ex, ey, kind, 0);
    Touch.Event[] evs = (pivot == null) ?
      new Touch.Event[] { main } :
      new Touch.Event[] { main, toTouch(event.time, 2*pivot.x-ex, 2*pivot.y-ey, kind, 1) };
    touchEvents.emit(evs);
  }

  private Touch.Event toTouch (double time, float x, float y, Touch.Event.Kind kind, int idoff) {
    return new Touch.Event(0, time, x, y, kind, currentId+idoff);
  }
}
