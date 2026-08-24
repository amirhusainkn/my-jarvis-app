import os
import threading
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock

# Pygame aur Plymouth crash errors ko bypass karne ke liye safe imports
try:
    from plyer import tts, call, flash
except Exception as e:
    tts = None
    call = None
    flash = None

try:
    import speech_recognition as sr
except Exception as e:
    sr = None

try:
    from jnius import autoclass
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    Intent = autoclass('android.content.Intent')
    ContactsContract = autoclass('android.provider.ContactsContract')
    ANDROID_ENV = True
except Exception as e:
    ANDROID_ENV = False


class JarvisUI(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        self.status_label = Label(
            text="Jarvis Ready Hai...\nMicrophone Permission Allow Karein",
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # App startup timing
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
        self.update_status("Jarvis Active Hai!\n'Jarvis' bolkar hukum kijiye.")
        
        # Audio thread start
        if sr:
            listener_thread = threading.Thread(target=self.listen_for_wakeword)
            listener_thread.daemon = True
            listener_thread.start()

    def update_status(self, text):
        self.status_label.text = text

    def open_app(self, package_name, app_name):
        if ANDROID_ENV:
            try:
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
        try:
            with sr.Microphone() as source:
                recognizer.adjust_for_ambient_noise(source, duration=1)
                while True:
                    try:
                        audio = recognizer.listen(source, phrase_time_limit=4)
                        command = recognizer.recognize_google(audio).lower()
                        if "jarvis" in command:
                            self.speak("Ji Sir...")
                            self.process_command(recognizer, source)
                    except Exception:
                        pass
        except Exception as err:
            print("Mic Error:", err)

    def process_command(self, recognizer, source):
        now = datetime.now()
        try:
            audio = recognizer.listen(source, phrase_time_limit=5)
            user_command = recognizer.recognize_google(audio).lower()
            
            Clock.schedule_once(lambda dt: self.update_status(f"Aapne kaha: {user_command}"))

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
                        self.speak("Torch feature support nahi kar raha.")

            else:
                self.speak("Ji Aamir bhai, main samajh gaya.")

        except Exception:
            self.speak("Aapki awaaz saaf nahi aayi.")


class JarvisApp(App):
    def build(self):
        return JarvisUI()


if __name__ == '__main__':
    JarvisApp().run()
    
