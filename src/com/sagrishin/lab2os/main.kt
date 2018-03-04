import kotlin.concurrent.thread

/* структура, в которой задается одна монета -- ее номинал и количество */
data class Coin(val coinValue: Int, var coinCount: Int = 0)

/* Флажок для первого потока */
@Volatile
var thread1Flag = true
/* Флажок для второго потока */
@Volatile
var thread2Flag = true
/* Номер потока, которыйсейчас может выполняться
 * 1 -- "основной поток"
 * 2 -- фоновый поток
 */
@Volatile
var threadNumber = 1
/* Сдача для выбранного билета. По условию мы всегда вводим 100 монет в автомат, значит сдача, например для билета 75
 * будет 100 - 75 = 25
 */
@Volatile
var newTicketRest = 0
/* список для вывода сдачи */
@Volatile
var resultRest: List<Int> = emptyList()

/*
 * Список с монетами и их номиналами
 */
val coins = listOf(
        Coin(1, 10),
        Coin(2),
        Coin(5, 3),
        Coin(10, 4),
        Coin(25, 3),
        Coin(50, 4)
)


fun main(args: Array<String>) {
    /*
     * основной поток. Я бы сказал, UI-Thread. Тут считывается номер авиа-билета и выводится размен, если он возможен.
     * Если введено "e" -- выходим, если введено 1-5, то пытаемся разменять сдачу за авиа-билет
     */
    val Thread1 = thread {
        /* Синтаксис ? означает, что переменная может быть null, и ты полностью осознаешь, что программа может из-за этого
         * завалиться. Таким образом компилятор "снимает" с себя ответственность за то, что у тебя в коде может быть null
         * (Kotlin -- NULL-безопасный язык, и не допускает NULL"ы в коде. Нуууу, как не допускает, ты может заставить
         * компилятор скомпилить код с NULL"ами, но тогда все пробемы с вылетами твои
         */
        var d: String? = ""

        while (d != "e") {
            printMenu()
            d = readLine()
            if (d == "e") {
                System.exit(0)
            } else if (d in listOf("1", "2", "3", "4", "5")) {
                /* d!! -- тут мы принудительно вызываем переменную, которая потенциально NULL */
                newTicketRest = 100 - listOf(28, 37, 50, 77, 91)[d!!.toInt() - 1]

                /* тут мы устанавливаем, что сейчас может начать работать поток два ... */
                threadNumber = 2
                /* ... соответственно, устанавливаем его флаг */
                thread2Flag = true

                /* тут мы просто ждем, пока не закончит свою работу поток два. Это и есть реализация алгоритма Деккера. */
                while (thread2Flag) {
                    if (threadNumber == 2) {
                        thread1Flag = false
                        while (threadNumber == 2) {
                        }
                        thread1Flag = true
                    }
                }

                /* как только поток два закончил выполнение, мы можем зайти в критическую секцию -- а именно вывести
                 * сдачу, если она есть, или вывести ошибку, если сдачи нет
                 */

                /* Если сумма элементов предполагаемой выдачу == сдаче для билета, то выдача возможна, иначе -- нет */
                if (resultRest.sum() == newTicketRest) {
                    println("rest: $resultRest")
                    println(coins)
                } else {
                    println("cannot calc rest")
                }
            } else {
                println("Error with entering, please try again")
            }
        }
        /* Как только пользователь нажал "e", мы освобождаем поток */
        thread1Flag = false
    }

    /* фоновый поток, в котором выполняется "бизнесс-логика" по расчету сдачию Тут мы просто крутимся в бесконечном цикле,
     * чтоб поток не закончил свою работу после первого вызова (короче, висящий поток, будет работать, пока работает программа)
     */
    val Thread2 = thread {
        while (true) {
            /* тоже реализация алгоритма Деккера. Тут мы ждем, пока не освободится "основной поток" */
            while (thread1Flag) {
                if (threadNumber == 1) {
                    thread2Flag = false
                    while (threadNumber == 1) {
                    }
                    thread2Flag = true
                }
            }

            /*  делаем размен*/
            resultRest = exchange(newTicketRest)

            /* переключаем threadNumber на "основной поток" и переставляем флаг основного потока в true */
            threadNumber = 1
            thread1Flag = true
        }
    }

    /* ждем, пока не закончится "основной поток". */
    Thread1.join()
    /* принудительно останавливаем фоновый поток */
    Thread2.interrupt()
}


/**
 * пытаемся найти размен для суммы [coinsToRest]. Возвращаем список с возможным разменом.
 * @param coinsToRest: Int   сумма, которую нужно разменять
 * @return List<Int>         список с разменом
 */
fun exchange(coinsToRest: Int): List<Int> {
    var div: Int
    var _coinsToRest = coinsToRest
    var res = emptyList<Int>()

    /*
     * тут мы проходим по списку {@see coins}, в уотором записаны номиналы и их количество в автомате.
     * Для начала мы фильтруем все монеты, количество которых == 0. Потом сортируем о номиналу от большего к меньшему.
     * Потом просто проходим по получившемуся массиву и пытаемся подобрать размен.
     * В _coinsToRest оставшаяся сумма для размена. Для каждой монеты {@see coin} мы делим _coinsToRest на количество
     * этой монеты в автомате (берем целое число -- сколько таких монет поместится в оставшейся выдаче). Дальше, если
     * мы можем выдать такое количество монет, то выдаем их, а в _coinsToRest пишем "остачу" (не помню, как по-русски) --
     * это то, сколько нам осталось разменять. Если нет такого количества, то мы записываем в сдачу столько, сколько можем,
     * а в _coinsToRest "остачу" + то, сколько нам не хватило для сдачи. И так повторяем, пока не пройдем по всем монетам,
     * от самой большой до самой маленькой. Для текущей монеты уменьшаем ее количество в автомате.
     * Пример:
     * есть число 131. Монеты: (1 10) (2 0) (5 0) (10 3) (25 2) (50 4), в формате (номинал количество). Список Res = []
     * 1) 131 / 50 = 2 * 50 + 31 => res.append(50, 50)
     * 2) 31 / 25 = 1 * 25 + 6   => res.append(25)
     * 3) 6 / 10 -- так выдать не можем такими монетами выдать сдачу (10 > 6), то в результат запишется ноль таких монет
     * пятерок и двоек у нас нет, поэтому их мы пропустим
     * 4) 6 / 1 => 6 * 1 => res.append(1, 1, 1, 1, 1, 1)
     * В итоге у нас массив: 50, 50, 25, 1, 1, 1, 1, 1, 1, что в сумме == 131
     * Собсно, это самая сложная часть этой лабы
     */
    coins.filter { it.coinCount > 0 }.sortedByDescending { it.coinValue }.forEach { coin ->
        div = _coinsToRest / coin.coinValue
        if (div <= coin.coinCount) {
            _coinsToRest %= coin.coinValue
            res += List(div) { coin.coinValue }
            coin.coinCount -= div
        } else {
            _coinsToRest = (div - coin.coinCount) * coin.coinValue + _coinsToRest % coin.coinValue
            res += List(coin.coinCount) { coin.coinValue }
            coin.coinCount -= div - coin.coinCount
        }
    }
    return res
}

/*
 * тут мы просто выводим меню
 *
 */
fun printMenu() {
    println("1: buy ticket to Kiev: 28 c")
    println("2: buy ticket to Moscow: 37 c")
    println("3: buy ticket to London: 50 c")
    println("4: buy ticket to Berlin: 77 c")
    println("5: buy ticket to Paris: 91 c")
    println("To exit, press 'e'")
}