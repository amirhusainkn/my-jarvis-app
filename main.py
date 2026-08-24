import os
import threading
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock

# Plyer Imports
try:
    from plyer import tts, call, flash
except Exception:
    tts = None
    call = None
    flash = None

# Speech Recognition Import
try:
    import speech_recognition as sr
except Exception:
    sr = None

# Android Native Permission & Intents
try:
    from jnius import autoclass
    from android.permissions import request_permissions, Permission
    ANDROID_ENV = True
except Exception:
    ANDROID_ENV = False


class JarvisUI(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        self.status_label = Label(
            text="Jarvis Active Hai!\n'Jarvis' bolkar hukum kijiye.",
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Android Native Permission Trigger
        if ANDROID_ENV:
            try:
                request_permissions([
                    Permission.RECORD_AUDIO,
                    Permission.READ_CONTACTS,
                    Permission.CALL_PHONE,
                    Permission.CAMERA
                ])
            except Exception as e:
                print("Permission Error:", e)

        Clock.schedule_once(self.welcome_speech, 2)

    def speak(self, text):
        print(f"Jarvis Speaking: {text}")
        if tts:
            try:
                tts.speak(text)
            except Exception as e:
                print("TTS Error:", e)

    def welcome_speech(self, dt):
        self.speak("Aadaab Aamir Bhai, main haazir hoon.")
        self.update_status("Driving Mode Active\n'Jarvis' Boliye...")
        
        if sr:
            listener_thread = threading.Thread(target=self.listen_for_wakeword)
            listener_thread.daemon = True
            listener_thread.start()

    def update_status(self, text):
        self.status_label.text = text

    def open_app(self, package_name, app_name):
        if ANDROID_ENV:
            try:
                PythonActivity = autoclass('org.kivy.android.PythonActivity')
                activity = PythonActivity.mActivity
                pm = activity.getPackageManager()
                intent = pm.getLaunchIntentForPackage(package_name)
                if intent:
                    activity.startActivity(intent)
                    self.speak(f"Ji Sir, {app_name} khol raha hoon.")
                else:
                    self.speak(f"Sir, {app_name} nahi mila.")
            except Exception as e:
                self.speak("App kholne mein masla aaya.")
        else:
            self.speak(f"Sir, {app_name} mobile par hi khulega.")

    def listen_for_wakeword(self):
        if not sr:
            return
        
        recognizer = sr.Recognizer()
        recognizer.dynamic_energy_threshold = False
        recognizer.energy_threshold = 300 

        while True:
            try:
                with sr.Microphone() as source:
                    recognizer.adjust_for_ambient_noise(source, duration=0.3)
                    Clock.schedule_once(lambda dt: self.update_status("Suno... Driving Mode Active\n'Jarvis' Boliye"))
                    
                    audio = recognizer.listen(source, phrase_time_limit=3, timeout=None)

                try:
                    command = recognizer.recognize_google(audio, language="hi-IN").lower()
                    Clock.schedule_once(lambda dt, c=command: self.update_status(f"Aapne Kaha: {c}"))
                    
                    if "jarvis" in command or "जार्विस" in command:
                        self.speak("Ji Aamir bhai...")
                        with sr.Microphone() as source2:
                            self.process_command(recognizer, source2)
                except sr.UnknownValueError:
                    pass
                except sr.RequestError:
                    pass

            except Exception as err:
                print("Mic Loop Repair Error:", err)

    def process_command(self, recognizer, source):
        now = datetime.now()
        try:
            audio = recognizer.listen(source, phrase_time_limit=5)
            user_command = recognizer.recognize_google(audio, language="hi-IN").lower()
            
            Clock.schedule_once(lambda dt, c=user_command: self.update_status(f"Hukum: {c}"))

            if "time" in user_command or "waqt" in user_command or "samay" in user_command:
                current_time = now.strftime("%I:%M %p")
                self.speak(f"Sir, abhi time {current_time} hua hai.")

            elif "date" in user_command or "tareekh" in user_command:
                current_date = now.strftime("%d %B %Y")
                self.speak(f"Sir, aaj ki tareekh {current_date} hai.")

            elif "youtube" in user_command:
                self.open_app("com.google.android.youtube", "YouTube")

            elif "whatsapp" in user_command:
                self.open_app("com.whatsapp", "WhatsApp")

            elif "torch" in user_command or "light" in user_command:
                if flash:
                    try:
                        flash.on()
                        self.speak("Torch jala di hai.")
                    except Exception:
                        self.speak("Torch support nahi kar raha.")

            else:
                self.speak("Ji Aamir bhai, main samajh gaya.")

        except Exception:
            self.speak("Aapki awaaz saaf nahi aayi.")


class JarvisApp(App):
    def build(self):
        return JarvisUI()


if __name__ == '__main__':
    JarvisApp().run()
            
