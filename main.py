import threading
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock
from kivy.graphics import Color, Rectangle

# Android Native Imports
try:
    from jnius import autoclass
    from android.permissions import request_permissions, Permission
    
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    Intent = autoclass('android.content.Intent')
    RecognizerIntent = autoclass('android.speech.RecognizerIntent')
    
    ANDROID_ENV = True
except Exception as e:
    print("Android Imports Error:", e)
    ANDROID_ENV = False


class MicTestUI(BoxLayout):
    def __init__(self, **kwargs):
        super(MicTestUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Clean Screen (Sirf Sunne Ka Status)
        self.status_label = Label(
            text="[color=00ff66][b]Listening...[/b][/color]",
            markup=True,
            font_size='22sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        if ANDROID_ENV:
            try:
                request_permissions([
                    Permission.RECORD_AUDIO,
                    Permission.READ_EXTERNAL_STORAGE,
                    Permission.WRITE_EXTERNAL_STORAGE
                ])
            except Exception as e:
                print("Permission Error:", e)

        Clock.schedule_once(self.trigger_speech_intent, 1.5)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def trigger_speech_intent(self, dt):
        try:
            activity = PythonActivity.mActivity
            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            
            activity.startActivity(intent)
        except Exception as e:
            print("Intent Error:", e)


class TestApp(App):
    def build(self):
        return MicTestUI()


if __name__ == '__main__':
    TestApp().run()
    
