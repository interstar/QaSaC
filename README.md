# QaSaC

QaSaC is the underlying target language / interpreter for the *Programming in Nature* project (of which more will be revealed in the near future).

The name QaSaC stands for "Queues and Stacks and Combinators" because these are some of the best things in Computer Science :-) 
And that's what QaSaC is made from.

The interpreter is currently written in Clojure because Clojure is a wonderfully powerful language and probably the easiest way to get a prototype of QaSaC up and running. Although eventually I hope we will get a faster and more efficient implementation, with fewer layers of Virtual Machine.


## Quickstart 

```
git clone https://github.com/interstar/QaSaC.git qasac
cd qasac
lein run examples/test1.qsc 30
```

## Overview

QaSaC is an experimental "esoteric" language (esolang) which somewhat in the family of "Forth-like" (concatenative / stack-based) languages. And somewhat in the family of data-flow languages. It's most immediate inspirations are Pure Data and Joy.

A QaSaC program consists of a number of very simple virtual nodes connected by queues. Each node runs a very short sub-program in an endless loop. This sub-program lives in its own thread, can read and write to a local data stack and communicates with the rest of the program through asynchronous queues.

Any particular node has a sub-program typically about the size of a Forth or Joy "word" (or a short function in Lisp). However it is also somewhat like an process in Erlang's Actor model, in that it loops forever within its own thread. Like programs for the Arduino or Processing, a sub-program also has two distinct phases : an initialize phase which is run once when it starts up, and a looping phase which runs continuously.

Here's a simple example : 

```
0 :/// :DUP :-> 1 :+ 
```

Note that this program consists of 6 tokens. What you are seeing here is not syntax intended for programmers, but the internal representation as seen by the Clojure interpreter. (And which a "reader" will eventually generate from a slightly prettier syntax.)

The `:///` is the separator between the startup and looping phase. Everything to the left of it is run once. Everything to the right runs in a continuous cycle. 

In the initialization phase here we have one token, the number zero. By default anything which isn't an operator is pushed onto the stack.

In the looping phase we have four instructions : 

`:DUP` dupicates whatever is on top of the stack. 

`:->` sends the value from the top of the stack to the default output queue.

1 is the number one. It's pushed onto the stack.

`:+` pops the top two items from the stack, adds them together and pushes the result back onto the stack.

At the end of the sequence, we go back to the beginning of the looping phase.

It should be fairly straightforward to simulate this program in your mind and see that it creates a stream of integers on its default output queue, starting at 0 and counting up by 1 each time. 

Here's another example :

```
:X 2 :* :->
```

All nodes have three input channels, named by convention `:X`, `:Y` and `:Z`. And two outputs, known as `:->` and `:+>`

This sub-program has no initialization phase. It starts immediately pulling in a value from :X and pushing it to the stack. The next two instructions multiply it by 2, and finally the result is pushed to the `:->` output channel. Wired correctly to our first node, this will multiply the stream of integers by 2. 

## Representing the Network

The flow-network syntax of QaSaC is yet to be released. But a simple text format is available. Allowing full programs to be written :

```
# These are the channels we require from the calling program.
! out

# And these are the ones we declare here
= c1

# one node, produces stream of integers
{} {:-> c1}
0 :/// :DUP :-> 1 :+

# second node, multiplies those integers by 3
{:X c1} {:-> out}
:X 3 :* :->

```

Lines starting with `#` are comments. These and blank lines are ignored.

A QaSaC program starts with two lines declaring channels. 

The line that starts with the `!` (bang / exclamation mark) declares named channels that the program expects to be passed by the interpreter.
All QaSaC programs are run asynchronously and the interpreter must communicate with them via Clojure's core.async channels. 

Note that the default interpreter we are using here passes one channel, named "out". It receives the output of the program on this channel and prints it to standard out. Our `!` line should contain only the declaration to use "out".

The line that starts with the `=` declares the internal channels. Eventually we'll have a more sophisticated interface which generates these automatically, but for the moment, they must be explicitly declared.

Having declared both external and internal channels, the rest of our program is now a sequence of node definitions.

Each node definition has two lines, the first declares connections between the local channel names within the node, and our channels as declared earlier. The second is the sequence of data and operators that define the node's behaviour. 

In our above example, the first node has its connections defined like so.

    {} {:-> c1}

The first mapping `{}` is empty. This node has no input channels.

The second, `{:-> c1}` specifies that the output known as `:->` actually goes to the channel `c1`.

The line defining node behaviour is the same as above : a generator of the sequence of integers, starting at 0, sending each of them out through `:->` (which is mapped to `c1`).

The second node declares its connections as `{:X c1} {:-> out}`

Here we're saying that `c1` is coming in through the `:X` input. And output (again via `:->`) is actually going to `out`, the main output of the program. The node definition multiplies everything coming in on `:X` by 3 and passes it on through `:->`.

Save this whole program somewhere as "test1.qsc" and run it (in the git repository) like this :

    lein run test1.qsc 30
    
    
QaSaC programs are assumed to run "forever", so the second parameter, the 30, is the number of items we want the interpreter to take from the out channel before finishing.

## More Stack Manipulation

`:DROP` drops the item from the top of the stack. 

Stack `<1 2 3>` becomes `<1 2>`

`:SWAP` swaps the top two items 

Stack `<1 2 3>` becomes `<1 3 2>`

`:NIP` removes the second item

Stack `<1 2 3>` becomes `<1 3>`

`:TUCK` copies the top item and places it behind the second. 

Stack `<1 2 3>` becomes `<1 3 2 3>`

## Operators

`:+`, `:-`, `:*` and `:/` are standard arithmetic operators. `:%` is modulus. 

`:=`, `:<` and `:>` are equals, less than and greater than.

 
 
## List, Blocks and Combinators

Like Joy, QaSaC uses combinators (or the equivalet of some of Lisp's special forms) instead of more traditional control structures. 

A combinator is an operator that takes blocks of code as arguments. Again like Joy, a block of code is the same as a list of operations, and the basic operation for turning a list back into executing code is the "unquote" on :UNQ operator.

For example 

```
[1 2 :+] :UNQ 
```

will first place the block [1 2 :+] on the stack and then unquote it, which will, in turn, place the numbers 1 and 2 on the stack and execute the + operation leaving 3 at the top of the stack.

In addition to :UNQ, QaSaC has three more combinators.

  
  - :COND is the conditional. 
  - :REP is simple repetition 
  - :LINREC is inspired by Joy's Linear Recursion combinator. 


:COND is the equivalent of Joy's ifte or conditional / selection structures in other languages.

```
:yes :no true :COND 
```

will leave a :yes at the top of the stack. Note that the true is Clojure's native boolean rather than a Clojure keyword.

Look at the following for a more complex and powerful example.

``` 
5 6 [:+] [:*] [:Y] :COND
```

Note several things. The values 5 and 6 are not in the yes or no blocks but are already on the stack. 

The conditional is actually a value arriving from the :Y queue. In this case the :Y queue is acting like a switch, selecting between adding and multiplying the arguments. 

:REP is a simple repetition

```
0 [1 :+] 5 :REP  
```

will start with 0 on the stack and execute the operation to add one to it, five times.

:LINREC is a generalization of most linear recursive algorithms. It works with 4 blocks on the stack.

```
test base inward outward :LINREC
```

test is a test to see if we've reached the bottom or base-case of the recursion.

base is what we do if we have reached the base-case.

if we haven't reached the base-case we're going to do this : 
- execute the "inward" block
- recurse 
- execute the outward  block


Here's a very simple example :

```
5 [:DUP 0 :=] [0] [1 :-] [:DUP :-> 1 :+] :LINREC
```

And here's how it works

We push 5 on the stack.

Now we push the 4 blocks used by the :LINREC onto the stack and finally call the :LINREC combinator itself.
 
The combinator executes the test [:DUP 0 :=]

this copies the top of the stack (initially 5) and tests if it's equal to 0.

It isn't 0, so we ignore the base block and go the inward block.

This subtracts 1 from the top of the stack (the 5 is decremented to 4)

The recursion takes place ... we're now testing if the 4 is equal to 0 ... it isn't so ...

eventually, the top of the stack *is* 0. So now we execute the base-case. Simply placing a zero on the stack.

At this point we're returning from the recursion and executing the outward block each time ... 

the outward block firstly duplicates the top of the stack ... and sends it to the standard out channel.

It then adds one to it.

This outward block is executed once for every level of recursion we're returning, so the effect of the whole is to pump out the numbers 0 to 4 on the out-chan. At which point, the :LINREC is finished.

:LINREC is a commonality on which its possible to build maps, folds and filters etc.

## Templates

Templates are another useful and powerful feature of QaSaC.  They allow for slots within a list to be filled from the stack. 

For example :

```
3 [2 :$$ :+] :TPL
```

will result in the value `[2 3 :+]` being left on the top of the stack. To execute it, simply unquote with :UNQ.

However, this block doesn't have to executed straight away. It might be stored on the stack for use by another combinator. Or even sent to an output channel to be executed elsewhere. 

Templates can also be used to assemble record-like structures out of lists.

```
[[:name :$$ :profession :$$] :TPL]
```

Is a block of code looking for two values to fill its two slots.

```
"Catbert" "Evil Director of Human Resources" [[:name :$$ :profession :$$] :TPL] :UNQ  
```
would fill it and deposit [:name "Catbert" :profession "Evil Director of Human Resources"]. 

## String Processing

We also give access to a string oriented templating ... based on the Clojure / Java format strings.

```
"World" "Hello %s" :STPL
```


## Futures

Futures are (perhaps appropriately) a new and experimental feature of QaSaC that feel like they might be pretty powerful, but I'm still experimenting with them to be really sure. **This is a very unstable feature. Futures may well be changed dramatically or removed altogether.**

At simplest, they turn QaSaC's postfix into a prefix notation. For example :

```
[] [:+] 2 :FUT
```

creates a Future on the top of the stack. A Future occupies the top of the stack in an unorthodox way. Instead of passively sitting there like any other object, it actively absorbs items pushed on top of it.

Let's see the above example in action.

Suppose we now try to push the number 7 onto the stack. We'd expect the stack to look like this : 
```
<[] [:+] 2 :FUT 7>
```
Instead, it looks like this :
```
<[7] [:+] 1 :FUT>
```

What happened? The Future keeps track of a block of code, a list of the arguments that will be fed to it and a count of how many arguments are outstanding.

Let's feed the Future again. This time with 12.


Now the Future is sated and it automatically fires. Firstly the arguments list is unquoted, leaving our stack looking like this :

<7 12 [:+]>

Then the block is automatically unquoted / executed, adding the two numbers and placing 19 on the stack.


So why should we care?

Well, Futures allow us to make blocks that act like prefix "functions".

[stuff here] 1 2 

Secondly, we can receive a block from another node, BEFORE we've got any data for it to process, and we can wrap it in Future
















 
















## Installation

Download from http://example.com/FIXME.

## Usage

The basic core program is designed to run an external QaSaC script and print n items from an output channel called "out".

You run a script like this :

    lein run examples/test1.qsc 20
    
test1.qsc is the script, 20 is the number to take from the output channel before terminating.

    
    

## Real Examples

Have a look at some examples. 

Here is test1.qsc 

    # These are the channels we require being given by the calling program.
    ! out
    
    # one node, produces stream of integers
    { } {:-> out}
    0 :/// :DUP :-> 1 :+


On the first line, starting with !, this program declares that it needs to be given an output channel with the name "out", 
by the interpreter. (Which is fine, this is what the default interpreter does.)

Lines starting with # are comments, and ignored.

The program consists  of a single node definition over two lines. 

The first line explicitly wires up the channels to the node. Inside the first curly-braces we have a hash of input channels the node is connected to. Here they are empty because this node requires no inputs. The second curly-braces has a hash of output channels. Here we see the pair :-> out which tells QaSaC that this node's default output (the :->) will go to the channel called "out". 

The second line defines the program running in the node itself. It's the same as described above.

Running this program with `lein run examples/test1.qsc 20`  will produce a sequence of the first 20 integers.

 



### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful

## License

Copyright © 2016 Phil Jones

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
