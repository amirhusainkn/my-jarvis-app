import time
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.utils import platform
from kivy.clock import Clock

if platform == 'android':
    from jnius import autoclass
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    TextToSpeech = autoclass('android.speech.tts.TextToSpeech')
    Locale = autoclass('java.util.Locale')

class SecureJarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(SecureJarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 40
        self.spacing = 20

        # स्टेटस दिखाने के लिए स्क्रीन पर टेक्स्ट
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Direct Mode Active[/b][/color]\n[size=14]तैयार और सक्रिय[/size]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.status_label.bind(size=self.status_label.setter('text_size'))
        self.add_widget(self.status_label)

        # कमांड देने के लिए बटन
        self.listen_btn = Button(
            text="Jarvis Se Baat Karein",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.listen_btn.bind(on_press=self.trigger_action)
        self.add_widget(self.listen_btn)

        # आवाज बोलने वाला इंजन (TTS) सेट करना
        self.tts_engine = None
        if platform == 'android':
            try:
                self.tts_engine = TextToSpeech(PythonActivity.mActivity, None)
                self.tts_engine.setLanguage(Locale.getDefault())
            except Exception as e:
                print("TTS Init Error:", e)

        # ऐप चालू होने के 2 सेकंड बाद वेलकम मैसेज
        Clock.schedule_once(self.welcome_speech, 2)

    def speak(self, text):
        self.status_label.text = f"[color=00f0ff][b]{text}[/b][/color]"
        if platform == 'android' and self.tts_engine:
            try:
                self.tts_engine.speak(text, TextToSpeech.QUEUE_FLUSH, None, None)
            except Exception as e:
                print("TTS Speak Error:", e)

    def welcome_speech(self, dt):
        greeting = "Aamir Hussain, Jarvis taiyar hai. Hukum kijiye."
        self.speak(greeting)

    def trigger_action(self, instance):
        # यहाँ हम सीधे कमांड प्रोसेस कर रहे हैं, बिना किसी बाहरी गूगल ऐप के दखल के
        self.process_command("samay batao")

    def process_command(self, query):
        query = query.lower()
        if "time" in query or "samay" in query or "waqt" in query:
            current_time = time.strftime("%I:%M %p")
            response = f"Abhi samay ho raha hai, {current_time}"
            self.speak(response)
        elif "kaun ho" in query or "who are you" in query:
            self.speak("Main aapka personal AI assistant Jarvis hoon, Aamir bhai.")
        else:
            response = "Aamir bhai, main aapka hukm مانने के लिए तैयार हूँ।"
            self.speak(response)

class JarvisApp(App):
    def build(self):
        return SecureJarvisAssistant()

if __name__ == '__main__':
    JarvisApp().run()
