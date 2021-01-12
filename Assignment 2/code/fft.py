import argparse
import matplotlib as plot
import numpy as np
import cv2 as cv
import sys
import math
import matplotlib.pyplot as plt
from cmath import exp, pi
from scipy.sparse import csr_matrix
import matplotlib.colors as colors
import time


# Takes a 1D array and returns its fourier transform
def naiveDFT(thisImage):
    n = len(thisImage)
    naive_ft_output = [sum((thisImage[k] * math.pow(math.e, -2 * math.pi * i * k / n) for k in range(n)))
                       for i in range(n)]
    return naive_ft_output


#resizes the image to the closest power of 2
def resize(thisImage):
    height = thisImage.shape[0]
    width = thisImage.shape[1]
    newHeight = 2 ** (height - 1).bit_length()
    newWidth = 2 ** (width - 1).bit_length()
    dim = (newWidth, newHeight)
    resized = cv.resize(thisImage, dim, interpolation=cv.INTER_AREA)
    return resized


#Checks for power of 2
def isPower2(n):
    return math.log(n, 2).is_integer()


def fft(elementList):
    n = len(elementList)
    if n <= 1:
        return elementList
    even = fft(elementList[0::2])
    odd = fft(elementList[1::2])
    T = [exp(-2j * pi * k / n) * odd[k] for k in range(n // 2)]
    return [even[k] + T[k] for k in range(n // 2)] + [even[k] - T[k] for k in range(n // 2)]


def invertFFT(elementList):
    newlist = fft([x.conjugate() for x in elementList])
    return [x.conjugate() / len(elementList) for x in newlist]


def denoise(thisImage):
    x = abs(thisImage.real.astype(np.float32))
    for a in range(0, thisImage.shape[0]):
        for b in range(0, thisImage.shape[1]):
            position = x[a][b] % (2 * pi)
            if (position > 2.93) and (position < 3.37): ####### to change to range of denoising, modify here ################
                thisImage[a][b] = 0                             
    return thisImage

def FFTDenoise(thisImage):
    fft_image = FFTDisplay(thisImage, False, False)
    denoised_image = denoise(fft_image)
    new_image = invert(denoised_image)
    return new_image

#compress starts from high frequency, 
#1 deletes 20%, 2 40%, 3 60%, 4 80%, 5 95%
def compress(thisImage, choice):
    x = abs(thisImage.real.astype(np.float32))
    nonZeroPixel = 0;
    for a in range(0, thisImage.shape[0]):
        for b in range(0, thisImage.shape[1]):
            position = (x[a][b] / (2 * pi)) % 1
            if choice == 1:
                if (position > 0.4) and (position < 0.6):    #####TO modify compressing range, change if statements
                    thisImage[a][b] = 0
                else:
                    nonZeroPixel += 1
            elif choice == 2:
                if (position > 0.3) and (position < 0.7):    #####TO modify compressing range, change if statements
                    thisImage[a][b] = 0
                else:
                    nonZeroPixel += 1
            elif choice == 3:
                if (position > 0.2) and (position < 0.8):    #####TO modify compressing range, change if statements
                    thisImage[a][b] = 0
                else:
                    nonZeroPixel += 1
            elif choice == 4:
                if (position > 0.1) and (position < 0.9):    #####TO modify compressing range, change if statements
                    thisImage[a][b] = 0
                else:
                    nonZeroPixel += 1
            else:
                if (position > 0.025) and (position < 0.975):     #####TO modify compressing range, change if statements
                    thisImage[a][b] = 0
                else:
                    nonZeroPixel += 1
    print("non-zero-pixel elements: ", nonZeroPixel)
    return thisImage


def compressSave(thisImage, choice):
    fft_image = FFTDisplay(thisImage, False, False)
    compressed_image = compress(fft_image, choice)
    new_image = invert(compressed_image)
    return new_image


def FFTDisplay(thisImage, verbose, nc):
    naive = nc
    if naive:
        # 1d fast fourier transform for each row
        if verbose:
            print("1d naive FT in rows")
            start = time.process_time()
        for a in range(0, thisImage.shape[0]):
            thisImage[a] = naiveDFT(thisImage[a])
            if verbose:
                print("row, ", a, ",of ", thisImage.shape[0])
        if verbose:
            # each column 2d fourier transform
            print("1d naive FT in coloum")
            start = time.process_time()
        for b in range(0, thisImage.shape[1]):
            thisImage[:, b] = naiveDFT(thisImage[:, b])
            if verbose:
                print("col, ", b, ",of ", thisImage.shape[1])
    else:
        cimg = np.array(thisImage, dtype=np.complex)
        # over rows
        if verbose:
            print("Calculating 1d fft over rows")
            start = time.process_time()
        for i in range(len(cimg[:, 0])):
            cimg[i] = fft(cimg[i])
        if verbose:
            print("Time: ", time.process_time() - start)
            b = abs(cimg.real.astype(np.float32))
            b = np.log(1 + np.around(b, 2))
            display(b)
           
            print("Calculating 1d fft over cols")
            start = time.process_time()
        #  over cols
        for i in range(len(cimg[0])):
            cimg[:, i] = fft(cimg[:, i])
        if verbose:
            print("Time: ", time.process_time() - start)
            print("2d fft of the image")
            b = abs(cimg.real.astype(np.float32))
            b = np.log(np.around(b, 2))
            display(b)
        return cimg

    return thisImage

def invert(thisImage):
    verbose = True 
    cimg = np.array(thisImage, dtype=np.complex)
    for i in range(len(cimg[0])):
        cimg[:, i] = invertFFT(cimg[:, i])
    for i in range(len(cimg[:, 0])):
        cimg[i] = invertFFT(cimg[i])

    b = abs(cimg.real.astype(np.float32))
    b = np.around(b, 2)
    return b


def plot():
    sys.stdout.write("plots graphs")


def display(an_img):
    plt.imshow(an_img, plt.cm.gray)
    plt.show()


def plot(thisImage):
    fft = []
    naive = []
    inv = []
    for i in range(1, 31):
        print("Run: ", i)
        dim = (int(math.pow(2, 5)), int(math.pow(2, 5)))
        image = thisImage.copy()
        img1 = cv.resize(image, dim, interpolation=cv.INTER_AREA)
        img2 = img1.copy()
        start = time.process_time()
        fftImage = FFTDisplay(img1, False, False)
        fft.append(time.process_time() - start)
        start = time.process_time()
        FFTDisplay(img2, False, True)
        naive.append(time.process_time() - start)
        start = time.process_time()
        invert(fftImage)
        inv.append(time.process_time() - start)
    
    # plot the graphs for different sizes
    x = [32, 64, 128, 256]
    y = []
    y1 = []
    y2 = []
    for i in range(5, 9):
        print("Image", i - 4)
        dim = (int(math.pow(2, i)), int(math.pow(2, i)))
        image = thisImage.copy()
        img = cv.resize(image, dim, interpolation=cv.INTER_AREA)
        print("Height: ", img.shape[0])
        print("Width: ", img.shape[1])
        start = time.process_time()
        fftImage = FFTDisplay(img, False, False)
        print("FFT Time: ", time.process_time() - start)
        y.append(time.process_time() - start)
        start = time.process_time()
        FFTDisplay(img, False, True)
        print("Naive Time: ", time.process_time() - start)
        y1.append(time.process_time() - start)
        start = time.process_time()
        invert(fftImage)
        print("Invert Time: ", time.process_time() - start)
        y2.append(time.process_time() - start)
    plt.plot(x, y, label="FFT", marker='o')
    plt.plot(x, y1, label="Naive", marker='o')
    plt.plot(x, y2, label="Invert_FFT", marker='o')
    plt.xlabel('Pixels')
    plt.ylabel('Time')
    plt.legend()
    plt.show()


def main(inputChoice, inputImage):
    try:
        # read an input image
        img = cv.imread(inputImage, cv.IMREAD_GRAYSCALE)
        img = img.astype(float)
        original_image = img
        if not isPower2(len(img)):
            img = resize(img)

    except:
        sys.stderr.write("Could not open file '%s'\n" % inputImage)
        exit(1)

    if inputChoice == "1":
        print("choice 1")
        fft_image = np.log(1 + np.abs(FFTDisplay(img, False, False)))
        p1 = plt.subplot(1, 2, 1)
        p1.imshow(original_image, plt.cm.gray)
        p2 = plt.subplot(1, 2, 2)
        p2.imshow(fft_image, plt.cm.gray)
        plt.show()

    elif inputChoice == "2":
        print("choice 2")
        final_image = FFTDenoise(img)
        p1 = plt.subplot(1, 2, 1)
        p1.imshow(img, plt.cm.gray)
        p2 = plt.subplot(1, 2, 2)
        p2.imshow(final_image, plt.cm.gray)
        plt.show()
        
    elif inputChoice == "3":
        print("choice 3")
        img1 = img.copy()
        img2 = img.copy()
        img3 = img.copy()
        img4 = img.copy()
        img5 = img.copy()

        print("Compression 1")
        final_image1 = compressSave(img1, 1)

        print("Compression 2")
        final_image2 = compressSave(img2, 2)

        print("Compression 3")
        final_image3 = compressSave(img3, 3)

        print("Compression 4")
        final_image4 = compressSave(img4, 4)

        print("Compression 5")
        final_image5 = compressSave(img5, 5)

        p1 = plt.subplot(2, 3, 1)
        p1.imshow(img, plt.cm.gray)
        p2 = plt.subplot(2, 3, 2)
        p2.imshow(final_image1, plt.cm.gray)
        p3 = plt.subplot(2, 3, 3)
        p3.imshow(final_image2, plt.cm.gray)
        p4 = plt.subplot(2, 3, 4)
        p4.imshow(final_image3, plt.cm.gray)
        p5 = plt.subplot(2, 3, 5)
        p5.imshow(final_image4, plt.cm.gray)
        p6 = plt.subplot(2, 3, 6)
        p6.imshow(final_image5, plt.cm.gray)
        plt.show()

        matrix1 = csr_matrix(img)
        matrix2 = csr_matrix(final_image1)
        matrix3 = csr_matrix(final_image2)
        matrix4 = csr_matrix(final_image3)
        matrix5 = csr_matrix(final_image4)
        matrix6 = csr_matrix(final_image5)
        # print(matrix1.toarray())

    elif inputChoice == "4":
        print("choice 4")
        plot(img)

    else:
        sys.stderr.write("invalid, choice should be 1-4")
        exit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='fft')

    parser.add_argument("-m",
                        "--inputChoice",
                        default="1",
                        help="fft choice, defaults to 1")

    parser.add_argument("-i",
                        "--inputImage",
                        default="./moonlanding.png",
                        help="input image filename")

    namespace = parser.parse_args()

    main(namespace.inputChoice,
         namespace.inputImage)