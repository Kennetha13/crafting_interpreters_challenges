module Main where


import Circle


main :: IO ()
main = do
    let circle = Circle 5
    

    putStrLn (describe circle)
    print (area circle)
