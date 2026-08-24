import threading
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock
from kivy.graphics import Color, Rectangle

# Plyer Text-to-Speech
try:
    from plyer import tts
except Exception:
    tts = None

# Speech Recognition
try:
    import speech_recognition as sr
except Exception:
    sr = None

# Android Permissions
try:
    from android.permissions import request_permissions, Permission
    ANDROID_ENV = True
except Exception:
    ANDROID_ENV = False


class MicTestUI(BoxLayout):
    def __init__(self, **kwargs):
        super(MicTestUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        # Background Color (Dark Theme)
        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Main Text Label (Glow Colors)
        self.status_label = Label(
            text="[color=00f0ff][b]INITIALIZING MIC...[/b][/color]",
            markup=True,
            font_size='22sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Request Permissions on Start
        if ANDROID_ENV:
            try:
                request_permissions([
                    Permission.RECORD_AUDIO,
                    Permission.READ_EXTERNAL_STORAGE,
                    Permission.WRITE_EXTERNAL_STORAGE
                ])
            except Exception as e:
                print("Permission Error:", e)

        Clock.schedule_once(self.start_mic_test, 2)

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

    def start_mic_test(self, dt):
        self.update_display("[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=ffaa00]Bolne ki koshish kijiye...[/color]")
        self.speak("Sir, Jarvis is ready!")

        # Start Background Microphone Listener
        if sr:
            t = threading.Thread(target=self.listen_loop)
            t.daemon = True
            t.start()
        else:
            self.update_display("[color=ff0000][b]ERROR: SpeechRecognition Not Found![/b][/color]")

    def listen_loop(self):
        recognizer = sr.Recognizer()
        recognizer.dynamic_energy_threshold = True

        while True:
            try:
                with sr.Microphone() as source:
                    recognizer.adjust_for_ambient_noise(source, duration=0.5)
                    
                    Clock.schedule_once(lambda dt: self.update_display(
                        "[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=00ff66]Listening... (Aap boliye)[/color]"
                    ))
                    
                    audio = recognizer.listen(source, phrase_time_limit=4)

                Clock.schedule_once(lambda dt: self.update_display(
                    "[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=ffff00]Processing voice...[/color]"
                ))

                command = recognizer.recognize_google(audio, language="hi-IN").lower()
                
                Clock.schedule_once(lambda dt, cmd=command: self.update_display(
                    f"[color=00f0ff][b]SIR, JARVIS IS READY![/b][/color]\n\n[color=ffffff]Aapne kaha:[/color] [color=ffaa00]'{cmd}'[/color]"
                ))

                if "time" in command or "samay" in command or "waqt" in command or "kitne baje" in command:
                    now = datetime.now()
                    current_time = now.strftime("%I:%M %p")
                    
                    reply = f"Sir, abhi time {current_time} hua hai."
                    Clock.schedule_once(lambda dt, t_str=current_time: self.update_display(
                        f"[color=00ff66][b]TIME: {t_str}[/b][/color]"
                    ))
                    self.speak(reply)

            except sr.UnknownValueError:
                pass
            except sr.RequestError:
                Clock.schedule_once(lambda dt: self.update_display(
                    "[color=ff0000][b]Internet Error (Google Speech API)[/b][/color]"
                ))
            except Exception as e:
                print("Mic Loop Exception:", e)


class TestApp(App):
    def build(self):
        return MicTestUI()


if __name__ == '__main__':
    TestApp().run()
                
