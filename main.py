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
    Intent = autoclass('android.content.Intent')
    RecognizerIntent = autoclass('android.speech.RecognizerIntent')
    ActivityResult = autoclass('android.app.Activity')

class JarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 40
        self.spacing = 20

        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Voice & Ears Active[/b][/color]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.status_label.bind(size=self.status_label.setter('text_size'))
        self.add_widget(self.status_label)

        self.listen_btn = Button(
            text="Jarvis se Baat Karein (Mic On)",
            font_size='18sp',
            size_hint=(1, 0.3),
            background_color=(0.1, 0.6, 0.9, 1)
        )
        self.listen_btn.bind(on_press=self.start_listening)
        self.add_widget(self.listen_btn)

        self.tts_engine = None
        if platform == 'android':
            try:
                self.tts_engine = TextToSpeech(PythonActivity.mActivity, None)
                self.tts_engine.setLanguage(Locale.getDefault())
            except Exception as e:
                print("TTS Init Error:", e)

        Clock.schedule_once(self.welcome_speech, 2)

    def speak(self, text):
        self.status_label.text = f"[color=00f0ff][b]{text}[/b][/color]"
        if platform == 'android' and self.tts_engine:
            try:
                self.tts_engine.speak(text, TextToSpeech.QUEUE_FLUSH, None, None)
            except Exception as e:
                print("TTS Speak Error:", e)

    def welcome_speech(self, dt):
        greeting = "Aamir Hussain, Jarvis ready hai. Boliye, kya kar sakta hoon?"
        self.speak(greeting)

    def start_listening(self, instance):
        if platform == 'android':
            try:
                self.status_label.text = "[color=ffff00][b]Suna ja raha hai... boliye![/b][/color]"
                intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Aamir bhai, kuch boliye...")
                
                activity = PythonActivity.mActivity
                activity.startActivityForResult(intent, 1000)
            except Exception as e:
                self.speak("Mic shuru karne mein kuch dikkat aayi hai.")
        else:
            self.process_command("time batao")

    def process_command(self, query):
        query = query.lower()
        if "time" in query or "samay" in query or "waqt" in query:
            current_time = time.strftime("%I:%M %p")
            response = f"Abhi samay ho raha hai, {current_time}"
            self.speak(response)
        elif "kaun ho" in query or "who are you" in query:
            self.speak("Main aapka personal AI assistant Jarvis hoon, Aamir bhai.")
        else:
            response = f"Aapne kaha hai: {query}"
            self.speak(response)


class JarvisApp(App):
    def build(self):
        return JarvisAssistant()


if __name__ == '__main__':
    JarvisApp().run()
    
