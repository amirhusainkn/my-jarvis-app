import threading
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock
from kivy.graphics import Color, Rectangle

# Plyer TTS
try:
    from plyer import tts
except Exception:
    tts = None

# Android Native Speech Recognition Imports
try:
    from jnius import autoclass, PythonJavaClass, java_method
    from android.permissions import request_permissions, Permission
    
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    Intent = autoclass('android.content.Intent')
    RecognizerIntent = autoclass('android.speech.RecognizerIntent')
    SpeechRecognizer = autoclass('android.speech.SpeechRecognizer')
    
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

        self.status_label = Label(
            text="[color=00f0ff][b]INITIALIZING JARVIS...[/b][/color]",
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

        Clock.schedule_once(self.start_app, 2)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def speak(self, text):
        if tts:
            try:
                tts.speak(text)
            except Exception as e:
                print("TTS Error:", e)

    def update_display(self, text):
        self.status_label.text = text

    def start_app(self, dt):
        self.update_display("[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=ffaa00]Listening active...[/color]")
        self.speak("Sir, Jarvis is ready!")
        
        # Start Speech Recognition Loop
        if ANDROID_ENV:
            Clock.schedule_once(self.start_listening, 1)
        else:
            self.update_display("[color=ff0000][b]ERROR: Android Native Engine Not Available[/b][/color]")

    def start_listening(self, dt):
        try:
            activity = PythonActivity.mActivity
            intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            
            self.update_display("[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=00ff66]Listening... (Aap Boliye)[/color]")
            
            # Start Activity for Result
            activity.startActivityForResult(intent, 1001)
        except Exception as e:
            print("Mic Start Error:", e)


class TestApp(App):
    def build(self):
        return MicTestUI()


if __name__ == '__main__':
    TestApp().run()
    
